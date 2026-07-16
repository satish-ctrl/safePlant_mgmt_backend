import os
from pypdf import PdfReader
from typing import List, Dict

def extract_text_from_pdf(pdf_path: str) -> List[Dict]:
    """Extracts text page-by-page from a PDF file."""
    if not os.path.exists(pdf_path):
        raise FileNotFoundError(f"PDF file not found at: {pdf_path}")
        
    reader = PdfReader(pdf_path)
    source_name = os.path.basename(pdf_path)
    pages = []
    
    for i, page in enumerate(reader.pages):
        text = page.extract_text()
        if text and text.strip():
            pages.append({
                'text': text.strip(),
                'page': i + 1,
                'source': source_name
            })
            
    print(f"Extracted {len(pages)} pages from {source_name}")
    return pages

def chunk_text(pages: List[Dict], chunk_size_words: int = 350, overlap_words: int = 40) -> List[Dict]:
    """Splits pages into overlapping chunks of words."""
    chunks = []
    
    for page in pages:
        text = page['text']
        words = text.split()
        
        i = 0
        chunk_idx = 0
        while i < len(words):
            chunk_words = words[i : i + chunk_size_words]
            chunk_text_str = " ".join(chunk_words)
            
            metadata = {
                'source': page['source'],
                'page': page['page'],
                'chunk_index': chunk_idx,
                'doc_id': f"{page['source']}_p{page['page']}_c{chunk_idx}"
            }
            
            chunks.append({
                'text': chunk_text_str,
                'metadata': metadata
            })
            
            chunk_idx += 1
            if len(chunk_words) < chunk_size_words:
                break
            i += (chunk_size_words - overlap_words)
            
    print(f"Created {len(chunks)} chunks from source pages.")
    return chunks
