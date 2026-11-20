-- ============================================================
-- AgroVision · Supabase Schema  v2
-- ============================================================
-- 部署：Supabase Dashboard → SQL Editor 全量粘贴执行。
--      脚本幂等，可重复执行；执行过程包在事务里，出错整体回滚。
--
-- 设计原则
--   1. 幂等       —— 重复执行不报错（policy/trigger 先 drop 再建）
--   2. 默认拒绝   —— 表全部启用 RLS，未显式授权的行不可读
--   3. 最小权限   —— anon 无任何表权限；authenticated 仅 CRUD 自己的行
--   4. 客户端可信 —— RPC 一律 security invoker，让 RLS 兜底，不靠函数内断言
--   5. 不存原图   —— detection_history 只落元数据，缩略图 ≤ 200×200 走 Storage
--
-- 相对 v1 的变更
--   · RPC upsert_plots 由 security definer 改为 security invoker 并锁定 search_path
--   · 修复 uuid/text 比较（remote_id 为空串时原写法会抛 22P02）
--   · 单条失败不再中断整批：逐条 exception 隔离，failed 计数据此生效
--   · 补齐 treatments.updated_at、check 约束、复合索引、序列授权
--   · 新增缩略图 bucket 与 storage.objects 策略（原 schema 缺失）
-- ============================================================

begin;

create extension if not exists pgcrypto;

-- ============ 0. 通用工具 ============

-- 毫秒时间戳，避免客户端与库内时钟口径不一致
create or replace function public.now_ms()
returns bigint
language sql
immutable
set search_path = pg_catalog
as $$
    select (extract(epoch from clock_timestamp()) * 1000)::bigint;
$$;

-- updated_at 自动维护（plots / treatments 的 bigint 毫秒列）
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = pg_catalog
as $$
begin
    new.updated_at := public.now_ms();
    return new;
end;
$$;

-- ============ 1. 用户档案 ============

create table if not exists public.user_profiles (
    user_id     uuid primary key references auth.users(id) on delete cascade,
    nickname    text not null default '农智眼用户'
                    check (char_length(nickname) between 1 and 32),
    role        text not null default 'USER'
                    check (role in ('USER', 'TECH', 'ADMIN')),
    avatar      text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

comment on table public.user_profiles is 'auth.users 的业务扩展；role 目前仅客户端展示，权限判定不依赖它';

-- ============ 2. 地块 ============

create table if not exists public.plots (
    id               bigserial primary key,
    remote_id        uuid not null default gen_random_uuid() unique,
    user_id          uuid not null references auth.users(id) on delete cascade,
    name             text not null check (char_length(name) between 1 and 64),
    crop_type        text not null,
    area             double precision check (area is null or area >= 0),
    longitude        double precision check (longitude is null or longitude between -180 and 180),
    latitude         double precision check (latitude is null or latitude between -90 and 90),
    phenology_stage  text not null default '',
    note             text not null default '' check (char_length(note) <= 500),
    is_deleted       boolean not null default false,
    created_at       bigint not null default public.now_ms(),
    updated_at       bigint not null default public.now_ms()
);

create index if not exists idx_plots_user_updated
    on public.plots (user_id, updated_at desc);
create unique index if not exists idx_plots_remote_id on public.plots (remote_id);

drop trigger if exists trg_plots_touch on public.plots;
create trigger trg_plots_touch
    before update on public.plots
    for each row execute function public.touch_updated_at();

-- ============ 3. 检测历史（只存元数据） ============

create table if not exists public.detection_history (
    id                bigserial primary key,
    remote_id         uuid not null default gen_random_uuid() unique,
    user_id           uuid not null references auth.users(id) on delete cascade,
    plot_remote_id    uuid references public.plots(remote_id) on delete set null,
    disease_name_cn   text not null default '',
    disease_name_en   text not null default '',
    confidence        real not null default 0 check (confidence >= 0 and confidence <= 1),
    all_results_json  text not null default '',
    thumbnail_url     text check (thumbnail_url is null or char_length(thumbnail_url) <= 512),
    timestamp         bigint not null default public.now_ms(),
    created_at        timestamptz not null default now()
);

create index if not exists idx_detection_user_ts
    on public.detection_history (user_id, timestamp desc);
create index if not exists idx_detection_plot
    on public.detection_history (plot_remote_id);

-- ============ 4. 用药记录 ============

create table if not exists public.treatments (
    id                    bigserial primary key,
    remote_id             uuid not null default gen_random_uuid() unique,
    user_id               uuid not null references auth.users(id) on delete cascade,
    plot_remote_id        uuid not null references public.plots(remote_id) on delete cascade,
    history_remote_id     uuid references public.detection_history(remote_id) on delete set null,
    chemical_name         text not null check (char_length(chemical_name) between 1 and 64),
    dosage                text not null,
    applied_at            bigint not null default public.now_ms(),
    safety_interval_days  int not null default 0 check (safety_interval_days between 0 and 365),
    expected_harvest_at   bigint,
    status                text not null default 'applied'
                              check (status in ('applied', 'planned', 'cancelled')),
    note                  text not null default '' check (char_length(note) <= 500),
    is_deleted            boolean not null default false,
    created_at            bigint not null default public.now_ms(),
    updated_at            bigint not null default public.now_ms()
);

create index if not exists idx_treatments_user_applied
    on public.treatments (user_id, applied_at desc);
create index if not exists idx_treatments_plot
    on public.treatments (plot_remote_id);
create index if not exists idx_treatments_harvest
    on public.treatments (expected_harvest_at)
    where expected_harvest_at is not null;

drop trigger if exists trg_treatments_touch on public.treatments;
create trigger trg_treatments_touch
    before update on public.treatments
    for each row execute function public.touch_updated_at();

-- ============ 5. RLS：默认拒绝 + 仅本人可读写 ============

alter table public.user_profiles      enable row level security;
alter table public.plots              enable row level security;
alter table public.detection_history  enable row level security;
alter table public.treatments         enable row level security;

-- 表属主同样受 RLS 约束，防止误用 postgres 连接绕过策略（service_role 的 BYPASSRLS 不受影响）
alter table public.user_profiles      force row level security;
alter table public.plots              force row level security;
alter table public.detection_history  force row level security;
alter table public.treatments         force row level security;

-- ---- user_profiles ----
drop policy if exists users_select_own_profile on public.user_profiles;
create policy users_select_own_profile on public.user_profiles
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists users_insert_own_profile on public.user_profiles;
create policy users_insert_own_profile on public.user_profiles
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists users_update_own_profile on public.user_profiles;
create policy users_update_own_profile on public.user_profiles
    for update to authenticated using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists users_delete_own_profile on public.user_profiles;
create policy users_delete_own_profile on public.user_profiles
    for delete to authenticated using ((select auth.uid()) = user_id);

-- ---- plots ----
drop policy if exists users_select_own_plots on public.plots;
create policy users_select_own_plots on public.plots
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists users_insert_own_plots on public.plots;
create policy users_insert_own_plots on public.plots
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists users_update_own_plots on public.plots;
create policy users_update_own_plots on public.plots
    for update to authenticated using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists users_delete_own_plots on public.plots;
create policy users_delete_own_plots on public.plots
    for delete to authenticated using ((select auth.uid()) = user_id);

-- ---- detection_history ----
drop policy if exists users_select_own_detections on public.detection_history;
create policy users_select_own_detections on public.detection_history
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists users_insert_own_detections on public.detection_history;
create policy users_insert_own_detections on public.detection_history
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists users_update_own_detections on public.detection_history;
create policy users_update_own_detections on public.detection_history
    for update to authenticated using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists users_delete_own_detections on public.detection_history;
create policy users_delete_own_detections on public.detection_history
    for delete to authenticated using ((select auth.uid()) = user_id);

-- ---- treatments ----
drop policy if exists users_select_own_treatments on public.treatments;
create policy users_select_own_treatments on public.treatments
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists users_insert_own_treatments on public.treatments;
create policy users_insert_own_treatments on public.treatments
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists users_update_own_treatments on public.treatments;
create policy users_update_own_treatments on public.treatments
    for update to authenticated using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists users_delete_own_treatments on public.treatments;
create policy users_delete_own_treatments on public.treatments
    for delete to authenticated using ((select auth.uid()) = user_id);

-- ============ 6. 表 / 序列授权 ============
-- anon 一律无表权限；authenticated 的四张表权限仍受 RLS 逐行约束

revoke all on public.user_profiles     from anon;
revoke all on public.plots             from anon;
revoke all on public.detection_history from anon;
revoke all on public.treatments        from anon;

grant select, insert, update, delete on public.user_profiles     to authenticated;
grant select, insert, update, delete on public.plots             to authenticated;
grant select, insert, update, delete on public.detection_history to authenticated;
grant select, insert, update, delete on public.treatments        to authenticated;

grant usage, select on all sequences in schema public to authenticated;

-- ============ 7. 缩略图 Storage ============
-- 路径约定：thumbnails/<user_id>/<remote_id>.webp，客户端只写自己的前缀

insert into storage.buckets (id, name, public, file_size_limit)
values ('thumbnails', 'thumbnails', false, 262144)  -- 256 KB 上限
on conflict (id) do nothing;

drop policy if exists "thumbnails_select_own" on storage.objects;
create policy "thumbnails_select_own" on storage.objects
    for select to authenticated
    using (bucket_id = 'thumbnails'
           and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "thumbnails_insert_own" on storage.objects;
create policy "thumbnails_insert_own" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'thumbnails'
                and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "thumbnails_delete_own" on storage.objects;
create policy "thumbnails_delete_own" on storage.objects
    for delete to authenticated
    using (bucket_id = 'thumbnails'
           and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ============ 8. 批量 upsert：plots ============
-- 客户端：POST /rest/v1/rpc/upsert_plots  { "items": [ ... ] }
-- 返回：{ synced, failed, ids: [{ local_id, remote_id } | { local_id, code, error }] }
--
-- security invoker：函数以内调用者身份执行，RLS 照常生效。
-- 即使函数内断言被绕过，也不可能写到别人名下。

create or replace function public.upsert_plots(items jsonb)
returns table (synced int, failed int, ids jsonb)
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    v_uid    uuid := (select auth.uid());
    v_item   jsonb;
    v_remote uuid;
    v_ok     int := 0;
    v_fail   int := 0;
    v_ids    jsonb := '[]'::jsonb;
    v_name   text;
    v_crop   text;
    v_local  jsonb;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;

    if items is null or jsonb_typeof(items) <> 'array' then
        raise exception 'items must be a jsonb array' using errcode = '22023';
    end if;

    for v_item in select value from jsonb_array_elements(items) loop
        v_local := v_item -> 'local_id';
        begin
            v_name := nullif(btrim(coalesce(v_item ->> 'name', '')), '');
            v_crop := nullif(btrim(coalesce(v_item ->> 'crop_type', '')), '');
            if v_name is null or v_crop is null then
                raise exception 'name and crop_type are required' using errcode = '22023';
            end if;

            -- 空串走新增，非法 uuid 直接落到下面的 exception 分支
            v_remote := nullif(v_item ->> 'remote_id', '')::uuid;

            if v_remote is null then
                insert into public.plots (
                    user_id, name, crop_type, area, longitude, latitude,
                    phenology_stage, note, is_deleted, created_at, updated_at
                ) values (
                    v_uid, v_name, v_crop,
                    nullif(v_item ->> 'area', '')::double precision,
                    nullif(v_item ->> 'longitude', '')::double precision,
                    nullif(v_item ->> 'latitude', '')::double precision,
                    coalesce(v_item ->> 'phenology_stage', ''),
                    coalesce(v_item ->> 'note', ''),
                    coalesce((v_item ->> 'is_deleted')::boolean, false),
                    coalesce((v_item ->> 'created_at')::bigint, public.now_ms()),
                    coalesce((v_item ->> 'updated_at')::bigint, public.now_ms())
                )
                returning remote_id into v_remote;
            else
                update public.plots set
                    name            = v_name,
                    crop_type       = v_crop,
                    area            = nullif(v_item ->> 'area', '')::double precision,
                    longitude       = nullif(v_item ->> 'longitude', '')::double precision,
                    latitude        = nullif(v_item ->> 'latitude', '')::double precision,
                    phenology_stage = coalesce(v_item ->> 'phenology_stage', ''),
                    note            = coalesce(v_item ->> 'note', ''),
                    is_deleted      = coalesce((v_item ->> 'is_deleted')::boolean, false),
                    updated_at      = coalesce((v_item ->> 'updated_at')::bigint, public.now_ms())
                where remote_id = v_remote and user_id = v_uid;

                if not found then
                    raise exception 'plot % not found or not owned by caller', v_remote
                        using errcode = '42501';
                end if;
            end if;

            v_ok := v_ok + 1;
            v_ids := v_ids || jsonb_build_object('local_id', v_local, 'remote_id', v_remote);

        exception when others then
            v_fail := v_fail + 1;
            v_ids := v_ids || jsonb_build_object(
                'local_id', v_local,
                'code', sqlstate,
                'error', sqlerrm
            );
        end;
    end loop;

    return query select v_ok, v_fail, v_ids;
end;
$$;

revoke all on function public.upsert_plots(jsonb) from public, anon;
grant execute on function public.upsert_plots(jsonb) to authenticated;

-- 触发器 / 时间函数同样收紧执行权限
revoke all on function public.now_ms() from public, anon;
revoke all on function public.touch_updated_at() from public, anon;

commit;
