import { useEffect, useState } from 'react';
import { api } from '../services/api';
import { Clock3, Search, Network } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import './timeline-v19.css';

export default function TimelinePage() {
  const [cases, setCases] = useState<any[]>([]);
  const [caseId, setCaseId] = useState('');
  const [rows, setRows] = useState<any[]>([]);
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [focusing, setFocusing] = useState(false);
  const nav = useNavigate();

  useEffect(() => {
    api.get('/cases').then(r => {
      setCases(r.data);
      if (r.data[0]) setCaseId(r.data[0].id);
    }).catch(() => setCases([]));
  }, []);

  useEffect(() => {
    if (!caseId) { setRows([]); return; }
    api.get('/timeline/cases/' + caseId).then(r => setRows(r.data)).catch(() => setRows([]));
  }, [caseId]);

  const focusInNetwork = async (row: any) => {
    const ids: string[] = Array.isArray(row.entityIds) ? row.entityIds : [];
    if (!ids.length || focusing) return;
    setFocusing(true);
    setSelected(`${row.timestamp}-${row.title}`);
    try {
      const active = await api.get('/sessions/active');
      const activeCaseId = active.data?.active ? active.data.caseId : null;
      if (activeCaseId !== caseId) {
        const started = await api.post('/sessions/start', null, { params: { caseId } });
        if (!started.data?.active) throw new Error('Unable to activate this case for network focus.');
      }
      nav(`/network?focus=${ids.join(',')}`);
    } catch (e: any) {
      alert(e?.response?.data?.message || e?.message || 'Unable to focus the selected event in Network Explorer.');
    } finally {
      setFocusing(false);
    }
  };

  const filtered = rows.filter(x => !q || `${x.type} ${x.title} ${x.detail}`.toLowerCase().includes(q.toLowerCase()));

  return <div>
    <div className="page-head"><div><div className="eyebrow">INVESTIGATION TIMELINE</div><h1>Case timeline</h1><p>Events are linked to the entities involved so investigators can jump directly into the network context.</p></div></div>
    <div className="toolbar"><select value={caseId} onChange={e => setCaseId(e.target.value)}><option value="">Select case</option>{cases.map(c => <option key={c.id} value={c.id}>{c.caseNumber} · {c.title}</option>)}</select><div className="search"><Search size={15}/><input placeholder="Filter timeline…" value={q} onChange={e => setQ(e.target.value)}/></div></div>
    <section className="panel timeline-panel"><div className="timeline-helper"><Network size={15}/><span>Click an entity-related event to open Network Explorer and highlight the entities involved.</span></div>{filtered.length ? filtered.map((x,i) => { const key=`${x.timestamp}-${x.title}-${i}`; const ids=Array.isArray(x.entityIds)?x.entityIds:[]; return <button className={`timeline-item timeline-clickable ${selected===key?'selected':''}`} key={key} onClick={() => ids.length ? void focusInNetwork(x) : setSelected(key)} type="button"><div className="timeline-marker"><Clock3 size={14}/></div><div className="timeline-time">{new Date(x.timestamp).toLocaleString()}</div><div className="timeline-body"><span className="pill">{x.type}</span><b>{x.title}</b><small>{x.detail}</small></div>{ids.length>0 && <span className="timeline-focus-action"><Network size={14}/>{focusing && selected===key ? 'Opening…' : 'View in network'}</span>}</button>; }):<div className="empty large">{caseId?'No timeline events found for this case.':'Select a case to view its timeline.'}</div>}</section>
  </div>;
}
