import { useCallback, useEffect, useRef, useState } from 'react'
import cytoscape, { Core } from 'cytoscape'
import maplibregl, { Map as MapLibreMap, Marker, Popup } from 'maplibre-gl'
import { RefreshCw, ZoomIn, ZoomOut, Maximize2, Save, LogOut, Search, X, ShieldCheck, MapPin, Crosshair, Link2, Shuffle, MousePointer2 } from 'lucide-react'
import { api } from '../services/api'
import type { GraphData, GraphNode } from '../types'
import { useNavigate, useSearchParams } from 'react-router-dom'
import 'maplibre-gl/dist/maplibre-gl.css'
import './network-v17.css'
import './network-v19.css'

const TYPE_META: Record<string, { color: string; label: string }> = {
  PERSON: { color: '#111111', label: 'Person' },
  PHONE: { color: '#b88700', label: 'Phone' },
  EMAIL: { color: '#6f42c1', label: 'Email' },
  VEHICLE: { color: '#c53b32', label: 'Vehicle' },
  LOCATION: { color: '#16845b', label: 'Location' },
  ORGANIZATION: { color: '#1f62a9', label: 'Organization' },
  BANK_ACCOUNT: { color: '#8a4a16', label: 'Bank account' },
  DOCUMENT: { color: '#68706b', label: 'Document' },
  EVENT: { color: '#ad276c', label: 'Event' }
}

const RELATIONSHIP_TYPES = [
  'ASSOCIATED_WITH', 'COMMUNICATES_WITH', 'USES', 'OWNS', 'CONNECTED_TO',
  'LOCATED_AT', 'OPERATES_FROM', 'SEIZED_AT', 'REFERENCES', 'TRANSACTS_WITH', 'KNOWS', 'LINKED_TO'
]

const graphStyles = [
  {
    selector: 'node',
    style: {
      'background-color': '#666',
      'border-width': 2,
      'border-color': '#111',
      shape: 'rectangle',
      label: 'data(code)',
      color: '#fff',
      'font-size': 9,
      'font-weight': 800,
      'text-valign': 'center',
      'text-halign': 'center',
      width: 52,
      height: 34,
      'overlay-opacity': 0,
      'text-wrap': 'none'
    }
  },
  ...Object.entries(TYPE_META).map(([type, meta]) => ({
    selector: `node[type = "${type}"]`,
    style: { 'background-color': meta.color, 'border-color': '#111', color: '#fff' }
  })),
  {
    selector: 'node:selected',
    style: {
      'border-width': 4,
      'border-color': '#b7f34a',
      'background-color': '#1d231e',
      'shadow-blur': 14,
      'shadow-opacity': 0.2,
      'shadow-color': '#111'
    }
  },
  { selector: 'node.search-hit', style: { 'border-width': 4, 'border-color': '#f2d94e', 'background-color': '#222' } },
  { selector: 'node.connect-source', style: { 'border-width': 4, 'border-color': '#b7f34a', 'border-style': 'dashed' } },
  { selector: 'node.timeline-focus', style: { 'border-width': 5, 'border-color': '#b7f34a', 'background-color': '#172017', 'shadow-blur': 18, 'shadow-opacity': 0.55, 'shadow-color': '#b7f34a' } },
  { selector: 'edge.timeline-focus-edge', style: { width: 4, 'line-color': '#b7f34a', 'target-arrow-color': '#b7f34a', opacity: 1 } },
  { selector: 'edge', style: { width: 1.8, 'line-color': '#a6ada7', 'target-arrow-color': '#7d837f', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier', label: 'data(type)', color: '#4b514d', 'font-size': 7.5, 'text-background-color': '#fff', 'text-background-opacity': 0.95, 'text-background-padding': 2, opacity: 0.82 } },
  { selector: 'edge[status = "VERIFIED"]', style: { width: 2.6, 'line-color': '#202520', 'target-arrow-color': '#202520', opacity: 1 } }
]

export default function NetworkPage() {
  const nav = useNavigate()
  const [searchParams] = useSearchParams()
  const focusParam = searchParams.get('focus') || ''
  const cyContainer = useRef<HTMLDivElement>(null)
  const cyRef = useRef<Core | null>(null)
  const mapContainer = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapLibreMap | null>(null)
  const markerRefs = useRef<Marker[]>([])
  const [session, setSession] = useState<any>(null)
  const [graph, setGraph] = useState<GraphData | null>(null)
  const [selected, setSelected] = useState<GraphNode | null>(null)
  const [term, setTerm] = useState('')
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [noActiveSession, setNoActiveSession] = useState(false)
  const [connectMode, setConnectMode] = useState(false)
  const [connectSource, setConnectSource] = useState<GraphNode | null>(null)
  const connectModeRef = useRef(false)
  const connectSourceRef = useRef<GraphNode | null>(null)
  const [connectTarget, setConnectTarget] = useState<GraphNode | null>(null)
  const [relationshipType, setRelationshipType] = useState('ASSOCIATED_WITH')
  const [relationshipConfidence, setRelationshipConfidence] = useState('80')
  const [relationshipSource, setRelationshipSource] = useState('MANUAL-OBSERVATION')
  const [creatingRelationship, setCreatingRelationship] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setMessage('')
    try {
      const s = await api.get('/sessions/active')
      if (!s.data.active) {
        setSession(null); setGraph(null); setNoActiveSession(true); return
      }
      setNoActiveSession(false)
      setSession(s.data)
      const r = await api.get(`/graph/cases/${s.data.caseId}`)
      setGraph(r.data)
      setSelected(null)
    } catch (e: any) {
      setMessage(e?.response?.data?.message || 'Unable to load the investigation network.')
    } finally { setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])

  useEffect(() => { connectModeRef.current = connectMode }, [connectMode])
  useEffect(() => { connectSourceRef.current = connectSource }, [connectSource])

  const createRelationship = async () => {
    if (!session || !connectSource || !connectTarget) return
    setCreatingRelationship(true); setMessage('')
    try {
      await api.post('/relationships', {
        caseId: session.caseId,
        sourceEntityId: connectSource.id,
        targetEntityId: connectTarget.id,
        relationshipType,
        confidence: Math.max(0, Math.min(100, Number(relationshipConfidence) || 0)) / 100,
        sourceReference: relationshipSource.trim() || 'MANUAL-OBSERVATION'
      })
      setMessage(`Relationship created: ${connectSource.referenceCode} → ${connectTarget.referenceCode}`)
      setConnectSource(null); setConnectTarget(null); setConnectMode(true)
      await load()
    } catch (e: any) {
      setMessage(e?.response?.data?.message || 'Unable to create relationship.')
    } finally { setCreatingRelationship(false) }
  }

  useEffect(() => {
    if (!cyContainer.current || !graph) return
    cyRef.current?.destroy()
    const cy = cytoscape({
      container: cyContainer.current,
      elements: [
        ...graph.nodes.map(n => ({ data: { id: n.id, code: n.referenceCode || n.id.slice(0, 6).toUpperCase(), type: n.type, label: n.label, node: n } })),
        ...graph.edges.map(e => ({ data: { id: e.id, source: e.source, target: e.target, type: e.type, status: e.status } }))
      ],
      style: graphStyles as any,
      wheelSensitivity: 0.22,
      minZoom: 0.2,
      maxZoom: 5
    })

    cy.layout({ name: 'random', animate: true, animationDuration: 450, fit: true, padding: 70 }).run()
    cy.on('tap', 'node', evt => {
      const node = evt.target.data('node') as GraphNode
      if (connectModeRef.current) {
        const source = connectSourceRef.current
        if (!source) {
          setConnectSource(node)
          setConnectTarget(null)
          setMessage(`Connection start selected: ${node.referenceCode}`)
        } else if (source.id === node.id) {
          setConnectSource(null)
          setMessage('Connection start cleared.')
        } else {
          setConnectTarget(node)
          setSelected(node)
          setMessage(`Connection target selected: ${node.referenceCode}`)
        }
        return
      }
      setSelected(node)
    })
    cyRef.current = cy
    return () => { cy.destroy(); cyRef.current = null }
  }, [graph])

  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return
    cy.nodes().removeClass('search-hit').removeClass('connect-source')
    if (connectSource) cy.getElementById(connectSource.id).addClass('connect-source')
    const q = term.trim().toLowerCase()
    if (!q) return
    cy.nodes().forEach(n => {
      const d = n.data()
      if (String(d.label).toLowerCase().includes(q) || String(d.code).toLowerCase().includes(q)) n.addClass('search-hit')
    })
  }, [term, connectSource])

  useEffect(() => {
    if (!mapContainer.current || mapRef.current || !graph) return
    const withLoc = graph.nodes.filter(n => n.locationLat != null && n.locationLng != null)
    if (!withLoc.length) return
    const first = withLoc[0]
    const map = new maplibregl.Map({
      container: mapContainer.current,
      style: { version: 8, sources: { osm: { type: 'raster', tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize: 256, attribution: '© OpenStreetMap contributors' } }, layers: [{ id: 'osm', type: 'raster', source: 'osm' }] },
      center: [first.locationLng!, first.locationLat!], zoom: 4.5, attributionControl: false
    })
    map.addControl(new maplibregl.NavigationControl({ showCompass: true }), 'top-right')
    mapRef.current = map
    withLoc.forEach(node => {
      const meta = TYPE_META[node.type] || { color: '#111', label: node.type }
      const el = document.createElement('button')
      el.className = 'entity-map-marker'
      el.style.borderColor = meta.color
      el.style.background = '#fff'
      el.textContent = node.mapCode || node.referenceCode.slice(0, 3)
      el.onclick = () => setSelected(node)
      const marker = new Marker({ element: el }).setLngLat([node.locationLng!, node.locationLat!]).setPopup(new Popup({ offset: 18 }).setHTML(`<strong>${node.referenceCode}</strong><br>${escapeHtml(node.label)}<br><small>${escapeHtml(node.locationLabel || 'No location label')}</small>`)).addTo(map)
      markerRefs.current.push(marker)
    })
    return () => { markerRefs.current.forEach(m => m.remove()); markerRefs.current = []; map.remove(); mapRef.current = null }
  }, [graph])

  useEffect(() => {
    const cy = cyRef.current
    if (!cy || !graph || !focusParam) return
    const ids = new Set(focusParam.split(',').map(x => x.trim()).filter(Boolean))
    if (!ids.size) return
    cy.nodes().removeClass('timeline-focus')
    cy.edges().removeClass('timeline-focus-edge')
    const focused = cy.nodes().filter(node => ids.has(node.id()))
    if (!focused.length) {
      setMessage('Timeline event loaded, but its entities are not present in the active case network.')
      return
    }
    focused.addClass('timeline-focus')
    cy.edges().forEach(edge => {
      if (ids.has(edge.source().id()) || ids.has(edge.target().id())) edge.addClass('timeline-focus-edge')
    })
    const first = focused[0]
    setSelected(first.data('node') as GraphNode)
    cy.fit(focused, 120)
    setMessage(`Timeline focus: ${focused.length} ${focused.length === 1 ? 'entity' : 'entities'} highlighted.`)
  }, [graph, focusParam])

  useEffect(() => {
    const map = mapRef.current
    if (!map || selected?.locationLat == null || selected?.locationLng == null) return
    map.flyTo({ center: [selected.locationLng, selected.locationLat], zoom: Math.max(map.getZoom(), 7), speed: 1.2 })
  }, [selected])

  const zoom = (delta: number) => cyRef.current?.zoom({ level: cyRef.current.zoom() + delta, renderedPosition: { x: 400, y: 260 } })
  const fit = () => cyRef.current?.fit(undefined, 60)
  const shuffle = () => cyRef.current?.layout({ name: 'random', animate: true, animationDuration: 450, fit: true, padding: 70 }).run()
  const reload = () => { void load() }
  const toggleConnect = () => { setConnectMode(v => !v); setConnectSource(null); setConnectTarget(null); setMessage('') }
  const save = async () => { if (session) { await api.post(`/sessions/${session.id}/save`, { graphZoom: cyRef.current?.zoom(), selectedEntity: selected?.id ?? null }); setMessage('Investigation state saved.') } }
  const exit = async () => { if (session) { await api.post(`/sessions/${session.id}/exit`); nav('/cases') } }

  if (loading) return <div className="center">Loading investigation network…</div>
  if (noActiveSession) return <div><div className="page-head"><div><div className="eyebrow">NETWORK EXPLORER</div><h1>No case active</h1><p>There is no active investigation case in this session.</p></div><button className="primary" onClick={() => nav('/cases')}>Open case files</button></div><div className="notice" role="status"><ShieldCheck size={16}/><div><b>No case active</b><span>Start an authorized investigation from Case Files to load the relationship network. Your saved case data remains unchanged.</span></div></div><div className="panel empty large">Network Explorer is ready. No case is currently active.</div></div>
  if (!graph || !session) return <div className="center">{message || 'Network unavailable.'}</div>

  const withLoc = graph.nodes.filter(n => n.locationLat != null && n.locationLng != null)

  return <div>
    <div className="page-head"><div><div className="eyebrow">NETWORK EXPLORER · {session.caseNumber}</div><h1>Case relationship network</h1><p>Explore the case graph, inspect entities, and build provisional relationships.</p></div><div className="head-actions"><button className={connectMode ? 'primary connect-active' : 'ghost'} onClick={toggleConnect}><Link2 size={15}/>{connectMode ? 'Exit connect mode' : 'Connect entities'}</button><button className="ghost" onClick={() => void save()}><Save size={15}/> Save</button><button className="ghost" onClick={() => void exit()}><LogOut size={15}/> Exit</button></div></div>
    {message && <div className="inline-status"><ShieldCheck size={15}/>{message}</div>}

    {connectMode && <div className="connect-banner"><div><b>Manual relationship mode</b><span>{connectSource ? `Start: ${connectSource.referenceCode} · now click another entity to choose the target.` : 'Click one entity, then click another entity to connect them.'}</span></div><div className="connect-step"><MousePointer2 size={14}/><span>{connectTarget ? `${connectSource?.referenceCode} → ${connectTarget.referenceCode}` : connectSource ? `${connectSource.referenceCode} → …` : 'Select source → target'}</span></div></div>}

    <div className="network-toolbar"><div className="search"><Search size={15}/><input aria-label="Search entities" placeholder="Search name or 6-character code…" value={term} onChange={e => setTerm(e.target.value)} />{term && <button className="icon-btn" onClick={() => setTerm('')}><X size={14}/></button>}</div><div className="graph-tools"><button title="Zoom out" onClick={() => zoom(-0.4)}><ZoomOut size={16}/></button><button title="Zoom in" onClick={() => zoom(0.4)}><ZoomIn size={16}/></button><button title="Fit network" onClick={fit}><Maximize2 size={16}/></button><button title="Scatter nodes" onClick={shuffle}><Shuffle size={16}/></button><button title="Reload from database" onClick={reload}><RefreshCw size={16}/></button></div></div>

    <div className="network-layout"><section className="panel graph-wrap network-graph-panel"><div ref={cyContainer} className="cytoscape-canvas" /><div className="graph-overlay-hint"><Crosshair size={14}/> Scroll to zoom · drag to pan · click to inspect{connectMode ? ' · click source then target to connect' : ''}</div><div className="graph-footer"><div className="legend">{Object.entries(TYPE_META).map(([k,v])=><span key={k}><i style={{background:v.color}} />{v.label}</span>)}</div><div className="graph-count">{graph.nodes.length} nodes · {graph.edges.length} links</div></div></section><aside className="panel inspector">{selected?<EntityInspector node={selected} onClose={() => setSelected(null)}/>:<div className="inspector-empty"><MapPin size={22}/><b>Select an entity</b><span>Click any node to inspect the complete available case record.</span></div>}</aside></div>

    <section className="panel table-panel"><div className="panel-head"><div><h2>Entity registry · current investigation</h2><small>The registration timestamp below belongs to this case association.</small></div></div><div className="table-scroll"><table><thead><tr><th>CODE</th><th>TYPE</th><th>ROLE</th><th>ENTITY</th><th>REGISTERED</th><th>LOCATION</th><th>SOURCE</th></tr></thead><tbody>{graph.nodes.map(n=><tr key={n.id} onClick={() => setSelected(n)}><td className="mono strong-code">{n.referenceCode}</td><td><span className="type-chip"><i style={{background:(TYPE_META[n.type]||{color:'#111'}).color}}/>{(TYPE_META[n.type]||{label:n.type}).label}</span></td><td>{n.caseRole || 'No information'}</td><td>{n.label}</td><td>{n.registeredAt ? new Date(n.registeredAt).toLocaleString() : 'No information'}</td><td>{n.locationLabel || 'No information'}</td><td>{n.sourceReference || 'No information'}</td></tr>)}</tbody></table></div></section>

    <section className="panel map-panel"><div className="panel-head"><div><h2>Case location map</h2><small>Markers use the entity map code and entity-type color.</small></div><MapPin size={18}/></div>{withLoc.length ? <div ref={mapContainer} className="map-canvas maplibre-canvas" /> : <div className="empty">No location information available for this investigation.</div>}</section>

    {connectMode && connectSource && connectTarget && <div className="modal-backdrop"><div className="modal relationship-modal"><div className="eyebrow">NEW RELATIONSHIP</div><h2>Connect entities</h2><p className="modal-subtitle">Create a provisional relationship in this case. You can verify it later.</p><div className="connection-preview"><div><span>FROM</span><b>{connectSource.referenceCode}</b><small>{connectSource.label}</small></div><Link2 size={20}/><div><span>TO</span><b>{connectTarget.referenceCode}</b><small>{connectTarget.label}</small></div></div><label>Relationship type<select value={relationshipType} onChange={e => setRelationshipType(e.target.value)}>{RELATIONSHIP_TYPES.map(type=><option key={type} value={type}>{type.replaceAll('_',' ')}</option>)}</select></label><div className="two"><label>Confidence %<input type="number" min="0" max="100" value={relationshipConfidence} onChange={e => setRelationshipConfidence(e.target.value)} /></label><label>Source reference<input value={relationshipSource} onChange={e => setRelationshipSource(e.target.value)} placeholder="MANUAL-OBSERVATION" /></label></div><div className="modal-actions"><button className="ghost" disabled={creatingRelationship} onClick={() => {setConnectTarget(null)}}>Cancel</button><button className="primary" disabled={creatingRelationship} onClick={() => void createRelationship()}>{creatingRelationship ? 'Creating…' : 'Create relationship'}</button></div></div></div>}
  </div>
}

function EntityInspector({node,onClose}:{node:GraphNode;onClose:()=>void}){const meta=TYPE_META[node.type]||{color:'#111',label:node.type}; const value=(v:any)=>v===null||v===undefined||v===''?'No information':String(v); return <><div className="inspect-top"><div><span className="inspect-code" style={{borderColor:meta.color}}>{node.referenceCode}</span><div className="eyebrow">{meta.label}</div><h2>{node.label}</h2></div><button className="icon-btn" onClick={onClose}><X size={16}/></button></div><div className="inspector-badges"><span className="pill green">{value(node.caseRole)}</span><span className="pill">{value(node.status)}</span></div><dl>{[['6-character code',node.referenceCode],['Map code',node.mapCode],['Alias',node.alias],['Date of birth',node.dateOfBirth],['Gender',node.gender],['Nationality',node.nationality],['Phone',node.phone],['Email',node.email],['Registered',node.registeredAt?new Date(node.registeredAt).toLocaleString():null],['Location',node.locationLabel],['Coordinates',node.locationLat!=null?`${node.locationLat}, ${node.locationLng}`:null],['Confidence',node.confidence!=null?`${Math.round(node.confidence*100)}%`:null],['Source',node.sourceReference],['Description',node.description]].map(([k,v])=><div key={k}><dt>{k}</dt><dd>{value(v)}</dd></div>)}</dl><div className="inspector-note"><ShieldCheck size={16}/><span>Investigative decisions should be grounded in source records and verification state.</span></div></>}

function escapeHtml(s:string){return s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]!))}
