export interface NformVO {
            /**
             * 表单ID
             */
            formId: string
            |
            number;

            /**
             * 表单名称
             */
            formName: string;

            /**
             * 表单描述
             */
            formDesc: string;

            /**
             * 表单内容
             */
            formContent: string;

}

export interface NformForm extends BaseEntity {
            /**
             * 表单名称
             */
            formName?: string;

            /**
             * 表单描述
             */
            formDesc?: string;

            /**
             * 表单内容
             */
            formContent?: string;


}

export interface NformQuery extends PageQuery {

            /**
             * 表单名称
             */
            formName?: string;

            /**
             * 表单描述
             */
            formDesc?: string;

            /**
             * 表单内容
             */
            formContent?: string;

    /**
     * 日期范围参数
     */
    params?: any;
}



