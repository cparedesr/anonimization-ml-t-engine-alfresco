from fastapi import FastAPI, File, UploadFile
from fastapi.responses import StreamingResponse
import fitz
import spacy
import re
from unidecode import unidecode
import io
import uvicorn

app = FastAPI()

# Configuración
ENTITY_LABELS_TO_TACH = ["PER", "DNI", "EMAIL", "ADDRESS"]
MARGIN_ADJUST = 2

# Cargar y configurar modelo NLP
nlp = spacy.load("es_core_news_lg", disable=["tagger", "parser", "lemmatizer"])

if "entity_ruler" not in nlp.pipe_names:
    ruler = nlp.add_pipe("entity_ruler", before="ner")
    patterns = [
        {"label": "DNI", "pattern": [{"TEXT": {"REGEX": r"^\d{8}[A-Za-z]$"}}]},
        {"label": "EMAIL", "pattern": [{"TEXT": {"REGEX": r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"}}]},
        {"label": "ADDRESS", "pattern": [
            {"LOWER": {"REGEX": r"^(calle|avda?|avenida|paseo|plaza|carrer|camino|ronda|ctra|carretera|urbanización|urb)\.?$"}},
            {"IS_ASCII": True, "OP": "+"},
            {"IS_DIGIT": True, "OP": "?"}
        ]},
    ]
    ruler.add_patterns(patterns)


def clean_text(text):
    text = unidecode(text)
    text = re.sub(r'\s+', ' ', text)
    text = re.sub(r'[^\w\s@.,-]', '', text)
    return text.strip()


def find_entity_rects(page, entity_text):
    rects = page.search_for(entity_text, quads=False, flags=fitz.TEXT_DEHYPHENATE)
    if not rects:
        rects = page.search_for(entity_text.lower(), quads=False, flags=fitz.TEXT_DEHYPHENATE)
    return rects


def anonimizar_pdf_stream(input_stream):
    doc = fitz.open(stream=input_stream, filetype="pdf")

    for page in doc:
        blocks = page.get_text("blocks", flags=fitz.TEXT_PRESERVE_LIGATURES | fitz.TEXT_DEHYPHENATE)

        for block in blocks:
            _, _, _, _, block_text, _, _ = block
            clean_block_text = clean_text(block_text)
            doc_nlp = nlp(clean_block_text)

            for ent in doc_nlp.ents:
                if ent.label_ == "PER" and len(ent.text.split()) < 2:
                    continue
                if ent.label_ in ENTITY_LABELS_TO_TACH:
                    rects = find_entity_rects(page, ent.text)
                    for rect in rects:
                        adjusted_rect = rect + (-MARGIN_ADJUST, -MARGIN_ADJUST, MARGIN_ADJUST, MARGIN_ADJUST)
                        page.add_redact_annot(adjusted_rect, fill=(0, 0, 0), cross_out=False)

        page.apply_redactions()

    output_stream = io.BytesIO()
    doc.save(output_stream, garbage=4, deflate=True, clean=True)
    doc.close()
    output_stream.seek(0)

    return output_stream


@app.post("/anonimizar-pdf")
async def anonimizar_endpoint(file: UploadFile = File(...)):
    pdf_bytes = await file.read()
    anonymized_pdf_stream = anonimizar_pdf_stream(pdf_bytes)

    return StreamingResponse(anonymized_pdf_stream, media_type="application/pdf", headers={"Content-Disposition": f"attachment; filename={file.filename}_anonimizado.pdf"})


if __name__ == "__main__":
    uvicorn.run("service-anonimizar:app", host="0.0.0.0", port=8000, reload=False)
