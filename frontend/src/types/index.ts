export type User={id:string;username:string;fullName:string;email?:string;roles:string[];status:string}
export type CaseItem={id:string;caseNumber:string;title:string;description?:string;category?:string;priority:string;status:string;classification:string;createdBy:string;createdByName:string;createdAt:string;updatedAt:string;deletedAt?:string;deletedBy?:string}
export type DashboardSummary={entitiesIdentified:number;activeConnections:number;sourceRecords:number;activeCases:number;priorityCounts:Record<string,number>}
export type GraphNode={id:string;type:string;label:string;referenceCode:string;mapCode?:string;caseRole?:string;registeredAt:string;updatedAt?:string;locationLabel?:string;locationLat?:number;locationLng?:number;status:string;description?:string;confidence?:number;alias?:string;dateOfBirth?:string;gender?:string;nationality?:string;phone?:string;email?:string;sourceReference?:string}
export type GraphEdge={id:string;source:string;target:string;type:string;confidence:number;status:string;sourceReference?:string;observedAt?:string;verifiedAt?:string;verifiedBy?:string}
export type GraphData={caseId:string;nodes:GraphNode[];edges:GraphEdge[];generatedAt:string}
export type Entity=GraphNode & {caseId:string;caseNumber:string;caseTitle:string;updatedAt:string}
export type AdminUser={id:string;username:string;fullName:string;email?:string;roles:string[];status:string;enabled:boolean;requestedRole?:string;createdAt:string;updatedAt:string;accessibleCases:string[]}
