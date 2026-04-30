export interface ClientQuery extends PageQuery {
  clientCode?: string;
  clientName?: string;
  clientType?: string;
  creditCode?: string;
}

export interface ClientVO {
  clientId: number;
  clientCode: string;
  clientName: string;
  clientNick: string;
  clientEn: string;
  clientDesc: string;
  clientType: string;
  clientLogo: string;
  address: string;
  provinceAreaId: number;
  cityAreaId: number;
  districtAreaId: number;
  streetId: number;
  addressLat: string;
  addressLng: string;
  webSite: string;
  email: string;
  tel: string;
  contact1: string;
  contact1Tel: string;
  contact1Emal: string;
  contact2: string;
  contact2Tel: string;
  contact2Emal: string;
  creditCode: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface ClientForm {
  clientId?: number;
  clientCode: string;
  clientName: string;
  clientNick?: string;
  clientEn?: string;
  clientDesc?: string;
  clientType?: string;
  clientLogo?: string;
  address?: string;
  provinceAreaId?: number;
  cityAreaId?: number;
  districtAreaId?: number;
  streetId?: number;
  addressLat?: string;
  addressLng?: string;
  webSite?: string;
  email?: string;
  tel?: string;
  contact1?: string;
  contact1Tel?: string;
  contact1Emal?: string;
  contact2?: string;
  contact2Tel?: string;
  contact2Emal?: string;
  creditCode?: string;
  remark?: string;
}
