"""Ask the Rail — FastAPI entry point.

POST /ask {"question": "..."} -> {"answer": "..."}
GET  /    -> minimal demo page

Run:  uvicorn app:app --port 8090
"""

import os

from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

from agent import run_agent
from tools import RailTools

app = FastAPI(title="Ask the Rail", version="1.0.0")

_tools = RailTools(
    mongo_uri=os.getenv("MONGO_URI", "mongodb://localhost:27017"),
    hub_api=os.getenv("HUB_API", "http://localhost:8080"),
)


class Question(BaseModel):
    question: str


@app.post("/ask")
def ask(q: Question):
    answer = run_agent(q.question, _tools.execute)
    return {"question": q.question, "answer": answer}


PAGE = """<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Ask the Rail</title>
<style>
  body { font-family: -apple-system, system-ui, sans-serif; max-width: 720px;
         margin: 48px auto; padding: 0 16px; background: #0d1117; color: #e6edf3; }
  h1 { font-size: 1.4rem; } .sub { color: #8b949e; margin-bottom: 24px; }
  form { display: flex; gap: 8px; }
  input { flex: 1; padding: 10px 12px; border-radius: 8px; border: 1px solid #30363d;
          background: #161b22; color: #e6edf3; font-size: 1rem; }
  button { padding: 10px 18px; border-radius: 8px; border: 0; background: #238636;
           color: white; font-size: 1rem; cursor: pointer; }
  #answer { margin-top: 24px; padding: 16px; border: 1px solid #30363d;
            border-radius: 8px; background: #161b22; white-space: pre-wrap;
            line-height: 1.5; min-height: 20px; }
  .examples { margin-top: 16px; color: #8b949e; font-size: 0.85rem; }
</style>
</head>
<body>
<h1>Ask the Rail</h1>
<div class="sub">Operations copilot for the RTPN clearing hub. Read-only, cites real audit records.</div>
<form onsubmit="ask(event)">
  <input id="q" placeholder="Why was payment X rejected?" autofocus>
  <button>Ask</button>
</form>
<div class="examples">Try: "how many payments were rejected and why" ·
"which participant has the lowest liquidity right now" ·
"show me ALPHA_BANK's recent rejected payments"</div>
<div id="answer"></div>
<script>
async function ask(e) {
  e.preventDefault();
  const box = document.getElementById('answer');
  box.textContent = 'Thinking…';
  const q = document.getElementById('q').value;
  try {
    const r = await fetch('/ask', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({question: q})
    });
    const data = await r.json();
    box.textContent = data.answer;
  } catch (err) {
    box.textContent = 'Error: ' + err;
  }
}
</script>
</body>
</html>"""


@app.get("/", response_class=HTMLResponse)
def index():
    return PAGE
