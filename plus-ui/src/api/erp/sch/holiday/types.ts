export interface HolidayQuery extends PageQuery {
  holidayName?: string;
  holidayType?: string;
  year?: number;
}

export interface HolidayVO {
  holidayId: number;
  holidayDate: string;
  holidayName: string;
  holidayType: string;
  year: number;
  isWorkingDay: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface HolidayForm {
  holidayId?: number;
  holidayDate?: string;
  holidayName?: string;
  holidayType?: string;
  year?: number;
  isWorkingDay?: number;
  remark?: string;
}
