<template>
  <el-drawer v-model="visible" title="3D 可视化配置" size="640px" append-to-body>
    <el-tabs v-model="activeTab">
      <!-- 位置与尺寸 -->
      <el-tab-pane label="位置与尺寸" name="position">
        <el-form label-width="100px">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="坐标X(米)">
                <el-input-number v-model="formData.posX" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="坐标Y(米)">
                <el-input-number v-model="formData.posY" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="坐标Z(米)">
                <el-input-number v-model="formData.posZ" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 库区尺寸字段 -->
          <el-row v-if="formData.areaId !== undefined" :gutter="20">
            <el-col :span="8">
              <el-form-item label="宽度(米)">
                <el-input-number v-model="formData.areaWidth" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="高度(米)">
                <el-input-number v-model="formData.areaHeight" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="深度(米)">
                <el-input-number v-model="formData.areaLength" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 库位尺寸字段 -->
          <el-row v-if="formData.locationId !== undefined" :gutter="20">
            <el-col :span="8">
              <el-form-item label="宽度(米)">
                <el-input-number v-model="formData.width" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="高度(米)">
                <el-input-number v-model="formData.height" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="深度(米)">
                <el-input-number v-model="formData.locationDepth" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="X轴旋转">
                <el-input-number v-model="formData.rotationX" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Y轴旋转">
                <el-input-number v-model="formData.rotationY" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Z轴旋转">
                <el-input-number v-model="formData.rotationZ" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>

      <!-- 外观样式 -->
      <el-tab-pane label="外观样式" name="style">
        <el-form label-width="100px">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="显示颜色">
                <el-color-picker v-if="formData.areaId !== undefined" v-model="formData.color" />
                <el-color-picker v-else v-model="formData.colorEmpty" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="透明度">
                <el-input-number v-model="formData.opacity" :precision="2" :min="0" :max="1" :step="0.1" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="显示标签">
                <el-switch v-model="formData.showLabel" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 库位特有颜色配置 -->
          <template v-if="formData.locationId !== undefined">
            <el-row :gutter="20">
              <el-col :span="6">
                <el-form-item label="空置颜色">
                  <el-color-picker v-model="formData.colorEmpty" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="占用颜色">
                  <el-color-picker v-model="formData.colorOccupied" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="锁定颜色">
                  <el-color-picker v-model="formData.colorLocked" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="预警颜色">
                  <el-color-picker v-model="formData.colorWarning" />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <!-- 边框配置 -->
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="显示边框">
                <el-switch v-model="formData.showBorder" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="边框颜色">
                <el-color-picker v-model="formData.borderColor" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="边框宽度">
                <el-input-number v-model="formData.borderWidth" :precision="1" :min="0" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>

      <!-- 高级配置 -->
      <el-tab-pane label="高级配置" name="advanced">
        <el-form label-width="100px">
          <!-- 库区特有配置 -->
          <template v-if="formData.areaId !== undefined">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="模型类型">
                  <el-select v-model="formData.modelType" placeholder="请选择模型类型" style="width: 100%">
                    <el-option label="立方体" value="CUBE" />
                    <el-option label="自定义" value="CUSTOM" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="模型URL">
                  <el-input v-model="formData.modelUrl" placeholder="自定义模型URL" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="显示边界墙">
                  <el-switch v-model="formData.showWalls" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="显示网格">
                  <el-switch v-model="formData.gridVisible" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <!-- 库位特有配置 -->
          <template v-if="formData.locationId !== undefined">
            <el-row :gutter="20">
              <el-col :span="6">
                <el-form-item label="货架ID">
                  <el-input v-model="formData.rackId" placeholder="货架ID" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="层数">
                  <el-input-number v-model="formData.layer" :min="1" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="列数">
                  <el-input-number v-model="formData.col" :min="1" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="行数">
                  <el-input-number v-model="formData.rowNum" :min="1" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="标签位置">
                  <el-select v-model="formData.labelPosition" placeholder="标签位置" style="width: 100%">
                    <el-option label="顶部" value="TOP" />
                    <el-option label="前面" value="FRONT" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="标签大小">
                  <el-input-number v-model="formData.labelSize" :precision="1" :min="0" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="配置JSON">
                <el-input v-model="formData.configJson" type="textarea" :rows="6" placeholder="3D配置JSON数据" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 3D 可视化配置抽屉组件
 * <p>
 * 供库区和库位管理页面共用，提供位置尺寸、外观样式、高级配置三个标签页。
 * 通过 v-model 控制显隐，formData 直接引用父组件表单数据。
 *
 * @author nexus
 */
const visible = defineModel<boolean>({ default: false })

const props = defineProps<{
  /** 表单数据引用，直接修改父组件的 form 对象 */
  formData: Record<string, any>
}>()

const activeTab = ref('position')
</script>
