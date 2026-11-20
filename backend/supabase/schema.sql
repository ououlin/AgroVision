-- ============================================================
-- AgroVision V2.0 Supabase Schema
-- ============================================================
-- 部署说明：
--   1. 在 Supabase Dashboard → SQL Editor 全量粘贴执行
--   2. 启用 Auth（手机号 + OTP）后，auth.users 表由 Supabase 托管
--   3. 业务表均带 user_id 外键，RLS 策略确保用户只能查/改自己的数据
--   4. 不存原图：detection_history.image_path 为空字符串；缩略图由客户端
--      压到 ≤200×200 后存到 Supabase Storage，URL 填到 thumbnail_url
-- ============================================================

-- ============ 1. 用户档案扩展（可选，auth.users 已含 phone） ============
create table if not exists public.user_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    nickname text not null default '农智眼用户',
    role text not null default 'USER',
    avatar text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- ============ 2. 地块表 ============
create table if not exists public.plots (
    id bigserial primary key,
    remote_id uuid not null default gen_random_uuid() unique,
    user_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    crop_type text not null,
    area double precision,
    longitude double precision,
    latitude double precision,
    phenology_stage text not null default '',
    note text not null default '',
    is_deleted boolean not null default false,
    created_at bigint not null,
    updated_at bigint not null
);

create index if not exists idx_plots_user_id on public.plots(user_id);
create index if not exists idx_plots_updated_at on public.plots(updated_at desc);

-- ============ 3. 检测历史元数据（不存原图） ============
create table if not exists public.detection_history (
    id bigserial primary key,
    remote_id uuid not null default gen_random_uuid() unique,
    user_id uuid not null references auth.users(id) on delete cascade,
    plot_remote_id uuid references public.plots(remote_id) on delete set null,
    disease_name_cn text not null default '',
    disease_name_en text not null default '',
    confidence real not null default 0,
    all_results_json text not null default '',
    thumbnail_url text,  -- Supabase Storage 上的 ≤200×200 缩略图 URL
    timestamp bigint not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_detection_user_id on public.detection_history(user_id);
create index if not exists idx_detection_plot on public.detection_history(plot_remote_id);
create index if not exists idx_detection_timestamp on public.detection_history(timestamp desc);

-- ============ 4. 用药记录 ============
create table if not exists public.treatments (
    id bigserial primary key,
    remote_id uuid not null default gen_random_uuid() unique,
    user_id uuid not null references auth.users(id) on delete cascade,
    plot_remote_id uuid not null references public.plots(remote_id) on delete cascade,
    history_remote_id uuid references public.detection_history(remote_id) on delete set null,
    chemical_name text not null,
    dosage text not null,
    applied_at bigint not null,
    safety_interval_days int not null default 0,
    expected_harvest_at bigint,
    status text not null default 'applied',
    note text not null default '',
    is_deleted boolean not null default false,
    created_at bigint not null
);

create index if not exists idx_treatments_user_id on public.treatments(user_id);
create index if not exists idx_treatments_plot on public.treatments(plot_remote_id);
create index if not exists idx_treatments_harvest on public.treatments(expected_harvest_at) where expected_harvest_at is not null;

-- ============ 5. RLS 行级安全策略 ============

alter table public.user_profiles enable row level security;
alter table public.plots enable row level security;
alter table public.detection_history enable row level security;
alter table public.treatments enable row level security;

-- user_profiles：用户只能 CRUD 自己的档案
create policy "users_select_own_profile" on public.user_profiles
    for select using (auth.uid() = user_id);
create policy "users_insert_own_profile" on public.user_profiles
    for insert with check (auth.uid() = user_id);
create policy "users_update_own_profile" on public.user_profiles
    for update using (auth.uid() = user_id);

-- plots：用户只能 CRUD 自己的地块
create policy "users_select_own_plots" on public.plots
    for select using (auth.uid() = user_id);
create policy "users_insert_own_plots" on public.plots
    for insert with check (auth.uid() = user_id);
create policy "users_update_own_plots" on public.plots
    for update using (auth.uid() = user_id);
create policy "users_delete_own_plots" on public.plots
    for delete using (auth.uid() = user_id);

-- detection_history：同上
create policy "users_select_own_detections" on public.detection_history
    for select using (auth.uid() = user_id);
create policy "users_insert_own_detections" on public.detection_history
    for insert with check (auth.uid() = user_id);
create policy "users_update_own_detections" on public.detection_history
    for update using (auth.uid() = user_id);
create policy "users_delete_own_detections" on public.detection_history
    for delete using (auth.uid() = user_id);

-- treatments：同上
create policy "users_select_own_treatments" on public.treatments
    for select using (auth.uid() = user_id);
create policy "users_insert_own_treatments" on public.treatments
    for insert with check (auth.uid() = user_id);
create policy "users_update_own_treatments" on public.treatments
    for update using (auth.uid() = user_id);
create policy "users_delete_own_treatments" on public.treatments
    for delete using (auth.uid() = user_id);

-- ============ 6. updated_at 自动更新触发器 ============
create or replace function public.touch_updated_at()
returns trigger as $$
begin
    new.updated_at := extract(epoch from now()) * 1000;
    return new;
end;
$$ language plpgsql;

drop trigger if exists trg_plots_touch on public.plots;
create trigger trg_plots_touch before update on public.plots
    for each row execute function public.touch_updated_at();

-- ============ 7. 批量 upsert 接口（供客户端调用） ============
-- 客户端调用 POST /rest/v1/rpc/upsert_plots
create or replace function public.upsert_plots(items jsonb)
returns table (synced int, failed int, ids jsonb) as $$
declare
    success_count int := 0;
    fail_count int := 0;
    result_ids jsonb := '[]'::jsonb;
    item json;
    local_id bigint;
    remote_id_val uuid;
begin
    for item in select * from jsonb_array_elements(items)
    loop
        local_id := (item->>'local_id')::bigint;
        remote_id_val := (item->>'remote_id')::uuid;

        if remote_id_val is null or remote_id_val = ''::text then
            -- 新增
            insert into public.plots (
                user_id, name, crop_type, area, longitude, latitude,
                phenology_stage, note, is_deleted, created_at, updated_at
            ) values (
                auth.uid(),
                item->>'name',
                item->>'crop_type',
                nullif(item->>'area', '')::double precision,
                nullif(item->>'longitude', '')::double precision,
                nullif(item->>'latitude', '')::double precision,
                item->>'phenology_stage',
                item->>'note',
                coalesce((item->>'is_deleted')::boolean, false),
                (item->>'created_at')::bigint,
                (item->>'updated_at')::bigint
            ) returning remote_id into remote_id_val;
        else
            -- 更新
            update public.plots set
                name = item->>'name',
                crop_type = item->>'crop_type',
                area = nullif(item->>'area', '')::double precision,
                longitude = nullif(item->>'longitude', '')::double precision,
                latitude = nullif(item->>'latitude', '')::double precision,
                phenology_stage = item->>'phenology_stage',
                note = item->>'note',
                is_deleted = coalesce((item->>'is_deleted')::boolean, false),
                updated_at = (item->>'updated_at')::bigint
            where remote_id = remote_id_val and user_id = auth.uid();
        end if;

        success_count := success_count + 1;
        result_ids := result_ids || jsonb_build_object('local_id', local_id, 'remote_id', remote_id_val);
    end loop;

    return query values (success_count, fail_count, result_ids);
end;
$$ language plpgsql security definer;
