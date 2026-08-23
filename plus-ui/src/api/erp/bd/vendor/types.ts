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
  streetName?: string;
  invoiceTitle?: string;
  invoiceAddressPhone?: string;
  invoiceBankName?: string;
  defaultInvoiceType?: string;
  buyerId?: number | string;
  buyerName?: string;
  deptId?: number | string;
  deptName?: string;
  attr1?: string;
  attr2?: string;
  attr3?: string;
  attr4?: string;
}

export interface VendorForm {
  vendorId?: number | string;
  vendorCode?: string;
  vendorName?: string;
  taxId?: string;
  address?: string;
  contactPerson?: string;
  phone?: string;
  bankAccount?: string;
  invoiceTitle?: string;
  invoiceAddressPhone?: string;
  invoiceBankName?: string;
  defaultInvoiceType?: string;
  industry?: string;
  region?: string;
  category?: string;
  status?: string;
  remark?: string;
  provinceAreaId?: number | string;
  cityAreaId?: number | string;
  districtAreaId?: number | string;
  streetId?: number | string;
  streetName?: string;
  addressLat?: string;
  addressLng?: string;
  buyerId?: number | string;
  buyerName?: string;
  deptId?: number | string;
  deptName?: string;
  attr1?: string;
  attr2?: string;
  attr3?: string;
  attr4?: string;
  createBy?: number | string;
  createTime?: string;
  updateBy?: number | string;
  updateTime?: string;
}
