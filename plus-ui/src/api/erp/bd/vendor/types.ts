export interface VendorQuery extends PageQuery {
  vendorCode?: string;
  vendorName?: string;
  industry?: string;
  region?: string;
  status?: string;
}

export interface VendorVO {
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  taxId: string;
  address: string;
  contactPerson: string;
  phone: string;
  bankAccount: string;
  industry: string;
  region: string;
  category: string;
  status: string;
  remark: string;
  provinceAreaId: number;
  cityAreaId: number;
  districtAreaId: number;
  streetId: number;
  addressLat: string;
  addressLng: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface VendorForm {
  vendorId?: number;
  vendorCode: string;
  vendorName: string;
  taxId?: string;
  address?: string;
  contactPerson?: string;
  phone?: string;
  bankAccount?: string;
  industry?: string;
  region?: string;
  category?: string;
  status?: string;
  remark?: string;
  provinceAreaId?: number;
  cityAreaId?: number;
  districtAreaId?: number;
  streetId?: number;
  addressLat?: string;
  addressLng?: string;
}
