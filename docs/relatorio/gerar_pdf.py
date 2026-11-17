"""Gera docs/Relatorio_Fase4_Grupo16.pdf a partir de docs/relatorio/relatorio.html.

Usa o Chrome em modo headless (protocolo DevTools) para imprimir o HTML em A4, com rodapé
numerado, e o pypdf para descobrir em que página cada seção caiu e preencher o sumário.

Uso (na raiz do repositório):
    uv run --with websocket-client --with pypdf python docs/relatorio/gerar_pdf.py [--video URL]
"""

import argparse
import base64
import io
import json
import pathlib
import subprocess
import tempfile
import time
import unicodedata
import urllib.request

import websocket
from pypdf import PdfReader, PdfWriter

RAIZ = pathlib.Path(__file__).resolve().parents[2]
HTML = RAIZ / "docs" / "relatorio" / "relatorio.html"
PDF = RAIZ / "docs" / "Relatorio_Fase4_Grupo16.pdf"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PORTA = 9339

CABECALHO = (
    '<div style="width:100%;margin:0 25mm;padding-bottom:3pt;border-bottom:0.6pt solid #111;'
    'font-family:Charter,Georgia,serif;font-size:9pt;color:#111;display:flex;justify-content:space-between;">'
    "<span>Plataforma de Feedback de Aulas</span><span>Relatório Técnico - Fase 4</span></div>"
)

RODAPE = (
    '<div style="width:100%;text-align:center;font-family:Charter,Georgia,serif;font-size:9.5pt;color:#111;">'
    '<span class="pageNumber"></span></div>'
)


class Navegador:
    def __init__(self):
        self.perfil = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.processo = subprocess.Popen(
            [CHROME, "--headless=new", f"--remote-debugging-port={PORTA}",
             f"--user-data-dir={self.perfil.name}", "--no-first-run", "about:blank"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        for _ in range(50):
            try:
                abas = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORTA}/json"))
                pagina = next(a for a in abas if a["type"] == "page")
                break
            except Exception:
                time.sleep(0.2)
        self.ws = websocket.create_connection(pagina["webSocketDebuggerUrl"], timeout=120)
        self.proximo_id = 0

    def chamar(self, metodo, **params):
        self.proximo_id += 1
        self.ws.send(json.dumps({"id": self.proximo_id, "method": metodo, "params": params}))
        while True:
            resposta = json.loads(self.ws.recv())
            if resposta.get("id") == self.proximo_id:
                if "error" in resposta:
                    raise RuntimeError(resposta["error"])
                return resposta["result"]

    def abrir(self, html):
        arquivo = pathlib.Path(tempfile.mkdtemp()) / "relatorio.html"
        arquivo.write_text(html, encoding="utf-8")
        self.chamar("Page.navigate", url=arquivo.as_uri())
        time.sleep(1.5)

    def avaliar(self, expressao):
        return self.chamar("Runtime.evaluate", expression=expressao, returnByValue=True)["result"].get("value")

    def imprimir(self, **opcoes):
        base = dict(paperWidth=8.27, paperHeight=11.69, preferCSSPageSize=True, printBackground=True)
        base.update(opcoes)
        return base64.b64decode(self.chamar("Page.printToPDF", **base)["data"])

    def fechar(self):
        self.ws.close()
        self.processo.terminate()
        self.processo.wait(timeout=10)
        self.perfil.cleanup()


def imprimir_documento(nav):
    capa = nav.imprimir(pageRanges="1", displayHeaderFooter=False)
    corpo = nav.imprimir(pageRanges="2-", displayHeaderFooter=True,
                         headerTemplate=CABECALHO, footerTemplate=RODAPE)
    saida = PdfWriter()
    for parte in (capa, corpo):
        for pagina in PdfReader(io.BytesIO(parte)).pages:
            saida.add_page(pagina)
    saida.add_metadata({"/Title": "Plataforma de Feedback de Aulas - Relatório Técnico - Fase 4",
                        "/Author": "Grupo 16 - FIAP Pós Tech ADJT"})
    buffer = io.BytesIO()
    saida.write(buffer)
    return buffer.getvalue()


def normalizar(texto):
    """Só letras e dígitos: a extração de texto do PDF quebra hífens e espaços de forma variável."""
    return "".join(c for c in unicodedata.normalize("NFKC", texto).lower() if c.isalnum())


def localizar_secoes(pdf, titulos):
    """Página (numeração física) de cada seção, procurando em ordem a partir da página 3
    (capa e sumário ocupam as duas primeiras) e sempre depois da seção anterior."""
    textos = [normalizar(p.extract_text() or "") for p in PdfReader(io.BytesIO(pdf)).pages]
    paginas, indice = {}, 2
    for id_secao, titulo in titulos.items():
        alvo = normalizar(titulo)
        while indice < len(textos) and alvo not in textos[indice]:
            indice += 1
        if indice == len(textos):
            break
        paginas[id_secao] = indice + 1
    return paginas


def main():
    argumentos = argparse.ArgumentParser()
    argumentos.add_argument("--video", help="URL do vídeo de apresentação")
    opcoes = argumentos.parse_args()

    html = HTML.read_text(encoding="utf-8")
    video = (f'<a href="{opcoes.video}">{opcoes.video}</a>' if opcoes.video
             else "<em>a ser incluído após a gravação</em>")
    html = html.replace("{{LINK_VIDEO}}", video)

    nav = Navegador()
    try:
        nav.abrir(html)
        titulos = nav.avaliar(
            "Object.fromEntries([...document.querySelectorAll('h1[id], h2[id]')]"
            ".map(h => [h.id, h.textContent.trim()]))"
        )
        paginas = localizar_secoes(imprimir_documento(nav), titulos)
        faltando = sorted(set(titulos) - set(paginas))
        if faltando:
            raise SystemExit(f"Seções não localizadas no PDF: {faltando}")

        nav.avaliar(
            "(() => { const p = %s; const ids = [...document.querySelectorAll('h1[id], h2[id]')].map(h => h.id);"
            " document.querySelectorAll('#sumario .pag').forEach((s, i) => s.textContent = p[ids[i]]); })()"
            % json.dumps(paginas)
        )
        PDF.write_bytes(imprimir_documento(nav))
        print(f"{PDF.relative_to(RAIZ)} gerado: {len(PdfReader(PDF).pages)} páginas")
    finally:
        nav.fechar()


if __name__ == "__main__":
    main()
