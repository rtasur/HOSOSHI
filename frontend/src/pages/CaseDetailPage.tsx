import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Eye, FileText, Play, ShieldCheck, Upload, Users } from 'lucide-react';
import { api } from '../services/api';

type EvidenceItem = { id: string; originalName: string; contentType: string; sizeBytes: number; uploadedBy: string; sha256?: string };
type SourceItem = { id: string; fileName: string; hasFile?: boolean };

export default function CaseDetailPage() {
  const { id } = useParams();
  const nav = useNavigate();
  const [c, setC] = useState<any>();
  const [evidence, setEvidence] = useState<EvidenceItem[]>([]);
  const [sourceDocuments, setSourceDocuments] = useState<SourceItem[]>([]);
  const [file, setFile] = useState<File>();
  const [message, setMessage] = useState('');

  const loadCaseData = async () => {
    if (!id) return;
    try {
      const [caseResponse, evidenceResponse, ingestionResponse] = await Promise.all([
        api.get('/cases/' + id),
        api.get('/evidence', { params: { caseId: id } }),
        api.get('/ingestion', { params: { caseId: id } }),
      ]);
      setC(caseResponse.data);
      setEvidence(evidenceResponse.data);
      setSourceDocuments(ingestionResponse.data.filter((item: SourceItem) =>
        Boolean(item.fileName && item.fileName !== 'Manual entry' && item.hasFile)));
    } catch (error: any) {
      setMessage(error.response?.data?.message || 'Unable to load the case.');
    }
  };

  useEffect(() => { void loadCaseData(); }, [id]);

  const investigate = async () => {
    try {
      await api.post('/sessions/start?caseId=' + id);
      nav('/network');
    } catch (error: any) {
      setMessage(error.response?.data?.message || 'Unable to start investigation.');
    }
  };

  const upload = async (event: FormEvent) => {
    event.preventDefault();
    if (!file || sourceDocuments.length === 0 || !id) return;
    const form = new FormData();
    form.append('caseId', id);
    form.append('file', file);
    try {
      await api.post('/evidence', form);
      setFile(undefined);
      setMessage('Evidence uploaded successfully.');
      await loadCaseData();
    } catch (error: any) {
      setMessage(error.response?.data?.message || 'Evidence upload failed.');
    }
  };

  const viewEvidenceFile = async (evidenceId: string, fileName: string) => {
    const opened = window.open('', '_blank');
    if (!opened) {
      setMessage('Please allow pop-ups to view the file.');
      return;
    }

    try {
      const response = await api.get(`/evidence/${evidenceId}/file`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      opened.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error: any) {
      opened.close();
      setMessage(error.response?.data?.message || `Unable to open ${fileName}.`);
    }
  };

  if (!c) return <div className="center">{message || 'Loading case…'}</div>;

  return (
    <div>
      <button className="back" onClick={() => nav('/cases')}><ArrowLeft size={14} /> Cases</button>
      <div className="page-head">
        <div><div className="eyebrow">CASE {c.caseNumber}</div><h1>{c.title}</h1><p>{c.description || 'No case description.'}</p></div>
        <div className="head-actions"><span className={'pill ' + c.priority.toLowerCase()}>{c.priority}</span><button className="primary" onClick={() => void investigate()}><Play size={15} /> Investigate</button></div>
      </div>

      <div className="detail-grid">
        <section className="panel">
          <h2>Case record</h2>
          <dl>
            <div><dt>Category</dt><dd>{c.category || 'No information'}</dd></div>
            <div><dt>Status</dt><dd>{c.status.replace(/_/g, ' ')}</dd></div>
            <div><dt>Classification</dt><dd>{c.classification}</dd></div>
            <div><dt>Created</dt><dd>{new Date(c.createdAt).toLocaleString()}</dd></div>
            <div><dt>Updated</dt><dd>{new Date(c.updatedAt).toLocaleString()}</dd></div>
          </dl>
        </section>
        <section className="panel">
          <h2>Case workspace</h2>
          <div className="quick-links">
            <button onClick={() => void investigate()}><Users size={16} /> Network analysis</button>
            <button onClick={() => nav('/entities')}><Users size={16} /> Entity registry</button>
            <button onClick={() => nav('/reports')}><FileText size={16} /> Investigation reports</button>
          </div>
        </section>
      </div>

      <section className="panel" style={{ marginTop: 14 }}>
        <div className="panel-head"><div><h2>Evidence vault</h2><small>Original case files stored with hash and uploader metadata.</small></div><ShieldCheck size={18} /></div>

        {sourceDocuments.length === 0 && <div className="notice">Upload a source document to this case before adding evidence.</div>}

        <form className="inline-form" onSubmit={upload}>
          <input type="file" accept=".pdf,.csv,.txt,.json,.png,.jpg,.jpeg" disabled={sourceDocuments.length === 0} onChange={event => setFile(event.target.files?.[0])} />
          <button className="primary small" disabled={!file || sourceDocuments.length === 0}><Upload size={13} /> Upload evidence</button>
        </form>

        {message && <div className="notice">{message}</div>}

        {evidence.length ? evidence.map(item => (
          <div className="ledger-row" key={item.id}>
            <div><b>{item.originalName}</b><small>{item.contentType} · {(item.sizeBytes / 1024).toFixed(1)} KB · {item.uploadedBy}</small></div>
            <span className="mono">{item.sha256?.slice(0, 12)}…</span>
            <button type="button" className="ghost small" onClick={() => void viewEvidenceFile(item.id, item.originalName)}><Eye size={13} /> View</button>
          </div>
        )) : <div className="empty">No evidence files registered for this case.</div>}
      </section>
    </div>
  );
}
