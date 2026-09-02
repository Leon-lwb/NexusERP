export interface EqHealthRecordVO {
  recordId: string;
  equipmentId: string;
  equipmentCode: string;
  equipmentName: string;
  iotDeviceId: string;
  score: number;
  grade: string;
  anomalyPoints: number;
  repairCount90d: number;
  openRepairCount: number;
  detailJson: string;
  prevScore: number | null;
  scoreChange: number | null;
  recordDate: string;
  createTime: string;
}

export interface EqHealthDetail {
  propertyCode: string;
  propertyName: string;
  mean: number;
  std: number;
  anomalyPoints: number;
}
