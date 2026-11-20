from pathlib import Path
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn

BASE = Path(__file__).resolve().parents[1]
MD_PATH = BASE / 'docs' / 'USER_MANUAL.md'
OUT_DIR = BASE / 'output' / 'doc'
OUT_DIR.mkdir(parents=True, exist_ok=True)
OUT_PATH = OUT_DIR / 'AgroVision_User_Manual.docx'


def apply_font(run, name='微软雅黑', size=None, bold=None, italic=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn('w:eastAsia'), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def add_bullets(doc, items):
    for item in items:
        p = doc.add_paragraph(style='List Bullet')
        p.paragraph_format.space_after = Pt(2)
        r = p.add_run(item)
        apply_font(r)


doc = Document()
sec = doc.sections[0]
sec.top_margin = Cm(2.2)
sec.bottom_margin = Cm(2.2)
sec.left_margin = Cm(2.4)
sec.right_margin = Cm(2.4)

styles = doc.styles
for sname in ['Normal', 'Title', 'Heading 1', 'Heading 2', 'Heading 3']:
    if sname in styles:
        styles[sname].font.name = '微软雅黑'
        styles[sname]._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
styles['Normal'].font.size = Pt(10.5)
styles['Title'].font.size = Pt(20)
styles['Heading 1'].font.size = Pt(15)
styles['Heading 2'].font.size = Pt(12)
styles['Heading 3'].font.size = Pt(11)

# Title page
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('农智眼 AgroVision 用户使用说明书')
apply_font(r, size=20, bold=True)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('适用版本：当前公开版本')
apply_font(r, italic=True)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('适用对象：果园种植户、农技人员、基地管理员、巡检人员、合作社及农业相关从业者')
apply_font(r, italic=True)

# Read markdown source and convert to a simple docx structure.
for raw in MD_PATH.read_text(encoding='utf-8').splitlines():
    line = raw.rstrip()
    if not line.strip():
        continue
    if line.startswith('>') or line.startswith('---'):
        continue
    if line.startswith('# '):
        p = doc.add_paragraph(style='Title')
        r = p.add_run(line[2:].strip())
        apply_font(r, size=18, bold=True)
        continue
    if line.startswith('## '):
        p = doc.add_paragraph(style='Heading 1')
        r = p.add_run(line[3:].strip())
        apply_font(r, size=15, bold=True)
        continue
    if line.startswith('### '):
        p = doc.add_paragraph(style='Heading 2')
        r = p.add_run(line[4:].strip())
        apply_font(r, size=12, bold=True)
        continue
    if line.startswith('- '):
        p = doc.add_paragraph(style='List Bullet')
        r = p.add_run(line[2:].strip())
        apply_font(r)
        continue
    if line.startswith('1. ') or line.startswith('2. ') or line.startswith('3. ') or line.startswith('4. ') or line.startswith('5. ') or line.startswith('6. ') or line.startswith('7. ') or line.startswith('8. ') or line.startswith('9. ') or line.startswith('10. '):
        p = doc.add_paragraph(style='List Number')
        r = p.add_run(line.strip())
        apply_font(r)
        continue
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run(line.strip())
    apply_font(r)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('© 2026 AgroVision Team. All rights reserved.')
apply_font(r, italic=True)

for para in doc.paragraphs:
    para.paragraph_format.line_spacing = 1.2

doc.save(OUT_PATH)
print(str(OUT_PATH))
