<template>
  <div class="designer-container">
    <!-- 添加窗口标题栏 -->
    <div class="designer-header">
      <h2>表单设计器</h2>
      <el-button type="primary" @click="handleClose">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>
    <div class="designer-content">
      <!-- 左侧组件列表 -->
      <div class="components-panel">
        <div class="panel-header">
          <h3>组件列表</h3>
        </div>
        <div class="components-list">
          <el-scrollbar>
            <!-- 基础组件 -->
            <div class="component-group">
              <div class="group-title">基础组件</div>
              <div class="component-items">
                <div
                  v-for="(item, index) in basicComponents"
                  :key="index"
                  class="component-item"
                  draggable="true"
                  @dragstart="handleDragStart($event, item)"
                >
                  <el-icon><component :is="item.icon" /></el-icon>
                  <span>{{ item.label }}</span>
                </div>
              </div>
            </div>

            <!-- 布局组件 -->
            <div class="component-group">
              <div class="group-title">布局组件</div>
              <div class="component-items">
                <div
                  v-for="(item, index) in layoutComponents"
                  :key="index"
                  class="component-item"
                  draggable="true"
                  @dragstart="handleDragStart($event, item)"
                >
                  <el-icon><component :is="item.icon" /></el-icon>
                  <span>{{ item.label }}</span>
                </div>
              </div>
            </div>

            <!-- 高级组件 -->
            <div class="component-group">
              <div class="group-title">高级组件</div>
              <div class="component-items">
                <div
                  v-for="(item, index) in advancedComponents"
                  :key="index"
                  class="component-item"
                  draggable="true"
                  @dragstart="handleDragStart($event, item)"
                >
                  <el-icon><component :is="item.icon" /></el-icon>
                  <span>{{ item.label }}</span>
                </div>
              </div>
            </div>
          </el-scrollbar>
        </div>
      </div>

      <!-- 中间画布区域 -->
      <div class="canvas-panel">
        <div class="panel-header">
          <h3>表单设计</h3>
          <div class="toolbar">
            <el-radio-group v-model="deviceType" size="small">
              <el-radio-button value="pc">PC端</el-radio-button>
              <el-radio-button value="mobile">移动端</el-radio-button>
            </el-radio-group>
            <el-button-group>
              <el-button type="primary" size="small" @click="handleSave">
                <el-icon><Download /></el-icon>保存
              </el-button>
              <el-button type="primary" size="small" @click="handlePreview">
                <el-icon><View /></el-icon>预览
              </el-button>
              <el-button type="primary" size="small" @click="handleGenerateCode">
                <el-icon><Document /></el-icon>生成代码
              </el-button>
            </el-button-group>
          </div>
        </div>
        <div class="canvas-content">
          <el-scrollbar>
            <div class="form-canvas" :class="{ 'mobile-view': deviceType === 'mobile' }" @dragover.prevent @drop="handleDrop">
              <el-form :model="formData" :label-width="deviceType === 'pc' ? '100px' : '80px'">
                <div
                  v-for="(item, index) in formComponents"
                  :key="item.id || index"
                  class="form-item"
                  :class="{ selected: currentComponent?.id === item.id }"
                  @click.capture="selectComponent(item)"
                  :data-component-id="item.id"
                >
                  <el-form-item :label="item.label">
                    <template v-if="item.type === 'input'">
                      <el-input v-model="formData[item.field]" v-bind="getFilteredProps(item.props)" />
                    </template>
                    <template v-else-if="item.type === 'textarea'">
                      <el-input type="textarea" v-model="formData[item.field]" v-bind="getFilteredProps(item.props)" />
                    </template>
                    <template v-else-if="item.type === 'select'">
                      <el-select v-model="formData[item.field]" v-bind="getFilteredProps(item.props)">
                        <el-option
                          v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                          :key="option.value"
                          :label="option.label"
                          :value="option.value"
                        />
                      </el-select>
                    </template>
                    <template v-else-if="item.type === 'checkbox'">
                      <el-checkbox-group
                        v-model="formData[item.field]"
                        v-bind="getFilteredProps(item.props)"
                        @change="(value) => handleFieldChange(value, item)"
                      >
                        <el-checkbox
                          v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                          :key="option.value"
                          :label="option.value"
                        >
                          {{ option.label }}
                        </el-checkbox>
                      </el-checkbox-group>
                    </template>
                    <template v-else-if="item.type === 'radio'">
                      <el-radio-group v-model="formData[item.field]" v-bind="getFilteredProps(item.props)">
                        <el-radio
                          v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                          :key="option.value"
                          :label="option.value"
                        >
                          {{ option.label }}
                        </el-radio>
                      </el-radio-group>
                    </template>
                    <template v-else-if="item.type === 'date'">
                      <el-date-picker
                        v-model="formData[item.field]"
                        type="date"
                        v-bind="getFilteredProps(item.props)"
                        :format="item.props.format || 'YYYY-MM-DD'"
                        :value-format="item.props.valueFormat || 'YYYY-MM-DD'"
                      />
                    </template>
                    <template v-else-if="item.type === 'datetime'">
                      <el-date-picker
                        v-model="formData[item.field]"
                        type="datetime"
                        v-bind="getFilteredProps(item.props)"
                        :format="item.props.format || 'YYYY-MM-DD HH:mm:ss'"
                        :value-format="item.props.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
                      />
                    </template>
                    <template v-else-if="item.type === 'time'">
                      <el-time-picker
                        v-model="formData[item.field]"
                        v-bind="getFilteredProps(item.props)"
                        :format="item.props.format || 'HH:mm:ss'"
                        :value-format="item.props.valueFormat || 'HH:mm:ss'"
                      />
                    </template>
                    <template v-else-if="item.type === 'popup'">
                      <PopupTable v-model="formData[item.field]" v-bind="getFilteredProps(item.props)" />
                    </template>
                    <template v-else-if="item.type === 'subtable'">
                      <div class="subtable-container" :data-component-id="item.id">
                        <div class="subtable-header">
                          <span>{{ item.label }}</span>
                          <div class="subtable-actions">
                            <el-button type="primary" size="small" @click="handleAddSubTableRow(item)"> 添加行 </el-button>
                          </div>
                        </div>
                        <div class="subtable-content">
                          <el-table :data="masterSubTableData[item.field]" border style="width: 100%">
                            <el-table-column
                              v-for="(column, colIndex) in item.subTableColumns"
                              :key="column.id || colIndex"
                              :prop="column.field"
                              :label="column.label"
                              :class="{ 'selected-column': currentSubTableColumn?.id === column.id }"
                            >
                              <template #header>
                                <div
                                  class="column-header"
                                  :class="{ 'selected-column': currentSubTableColumn?.id === column.id }"
                                  @click.stop="selectSubTableColumn(item, column)"
                                >
                                  <div class="column-title">{{ column.label }}</div>
                                  <div class="column-actions" v-if="currentSubTableColumn?.id === column.id">
                                    <el-button
                                      type="danger"
                                      size="small"
                                      data-action="delete-subtable-column"
                                      @click.stop="handleDeleteSubTableColumn(item, column)"
                                      @mousedown.stop
                                    >
                                      <el-icon @click.stop="handleDeleteSubTableColumn(item, column)"><Delete /></el-icon>
                                    </el-button>
                                    <el-button type="primary" size="small" @click.stop="handleCopySubTableColumn(item, column)" @mousedown.stop>
                                      <el-icon><CopyDocument /></el-icon>
                                    </el-button>
                                  </div>
                                </div>
                              </template>
                              <template #default="scope">
                                <template v-if="column.type === 'input'">
                                  <el-input v-model="scope.row[column.field]" v-bind="column.props" />
                                </template>
                                <template v-else-if="column.type === 'textarea'">
                                  <el-input type="textarea" v-model="scope.row[column.field]" v-bind="column.props" />
                                </template>
                                <template v-else-if="column.type === 'select'">
                                  <el-select v-model="scope.row[column.field]" v-bind="column.props">
                                    <el-option
                                      v-for="option in column.props.dataSource === 'dict'
                                        ? dictOptions[column.props.dictType] || []
                                        : column.props.options"
                                      :key="option.value"
                                      :label="option.label"
                                      :value="option.value"
                                    />
                                  </el-select>
                                </template>
                                <template v-else-if="column.type === 'checkbox'">
                                  <el-checkbox-group v-model="scope.row[column.field]" v-bind="column.props">
                                    <el-checkbox
                                      v-for="option in column.props.dataSource === 'dict'
                                        ? dictOptions[column.props.dictType] || []
                                        : column.props.options"
                                      :key="option.value"
                                      :label="option.value"
                                    >
                                      {{ option.label }}
                                    </el-checkbox>
                                  </el-checkbox-group>
                                </template>
                                <template v-else-if="column.type === 'radio'">
                                  <el-radio-group v-model="scope.row[column.field]" v-bind="column.props">
                                    <el-radio
                                      v-for="option in column.props.dataSource === 'dict'
                                        ? dictOptions[column.props.dictType] || []
                                        : column.props.options"
                                      :key="option.value"
                                      :label="option.value"
                                    >
                                      {{ option.label }}
                                    </el-radio>
                                  </el-radio-group>
                                </template>
                                <template v-else-if="column.type === 'date'">
                                  <el-date-picker v-model="scope.row[column.field]" type="date" v-bind="column.props" />
                                </template>
                                <template v-else-if="column.type === 'datetime'">
                                  <el-date-picker v-model="scope.row[column.field]" type="datetime" v-bind="column.props" />
                                </template>
                                <template v-else-if="column.type === 'time'">
                                  <el-time-picker v-model="scope.row[column.field]" v-bind="column.props" />
                                </template>
                                <template v-else-if="column.type === 'popup'">
                                  <PopupTable v-model="scope.row[column.field]" v-bind="column.props" />
                                </template>
                                <template v-else>
                                  <el-input v-model="scope.row[column.field]" v-bind="column.props" />
                                </template>
                              </template>
                            </el-table-column>
                            <el-table-column label="操作" width="150">
                              <template #default="scope">
                                <el-button
                                  size="small"
                                  type="danger"
                                  data-action="delete-subtable-row"
                                  @click.stop="handleDeleteSubTableRow(item, scope.$index)"
                                  @mousedown.stop
                                >
                                  删除
                                </el-button>
                              </template>
                            </el-table-column>
                          </el-table>
                        </div>
                        <div class="subtable-drop-area" @dragover.prevent.stop @drop.prevent.stop="handleSubTableDrop($event, item)">
                          <el-empty v-if="!item.subTableColumns?.length" description="拖拽组件到此处添加列" />
                        </div>
                      </div>
                    </template>
                    <template v-else-if="item.type === 'grid'">
                      <el-row v-bind="item.props">
                        <el-col v-for="(col, colIndex) in item.children" :key="col.id || colIndex" :span="col.span">
                          <div class="layout-drop-area" @dragover.prevent @drop="handleLayoutDrop($event, col)">
                            <template v-if="col.children && col.children.length > 0">
                              <div
                                v-for="(child, childIndex) in col.children"
                                :key="child.id || childIndex"
                                class="form-item"
                                :class="{ selected: currentComponent?.id === child.id }"
                                @click.stop="selectComponent(child)"
                                :data-component-id="child.id"
                              >
                                <el-form-item :label="child.label">
                                  <template v-if="child.type === 'date'">
                                    <el-date-picker
                                      v-model="formData[child.field]"
                                      type="date"
                                      v-bind="getFilteredProps(child.props)"
                                      :format="child.props.format || 'YYYY-MM-DD'"
                                      :value-format="child.props.valueFormat || 'YYYY-MM-DD'"
                                    />
                                  </template>
                                  <template v-else-if="child.type === 'datetime'">
                                    <el-date-picker
                                      v-model="formData[child.field]"
                                      type="datetime"
                                      v-bind="getFilteredProps(child.props)"
                                      :format="child.props.format || 'YYYY-MM-DD HH:mm:ss'"
                                      :value-format="child.props.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
                                    />
                                  </template>
                                  <template v-else-if="child.type === 'time'">
                                    <el-time-picker
                                      v-model="formData[child.field]"
                                      v-bind="getFilteredProps(child.props)"
                                      :format="child.props.format || 'HH:mm:ss'"
                                      :value-format="child.props.valueFormat || 'HH:mm:ss'"
                                    />
                                  </template>
                                  <template v-else>
                                    <component
                                      :is="getComponentByType(child.type)"
                                      v-model="formData[child.field]"
                                      v-bind="getFilteredProps(child.props)"
                                    />
                                  </template>
                                </el-form-item>
                                <div v-if="currentComponent?.id === child.id" class="form-item-actions">
                                  <el-button
                                    type="danger"
                                    size="small"
                                    data-action="delete-root-component"
                                    @click.stop="onDeleteClick"
                                    @mousedown.stop
                                  >
                                    <el-icon @click.stop="onDeleteClick"><Delete /></el-icon>
                                  </el-button>
                                  <el-button type="primary" size="small" @click.stop="handleCopyComponent" @mousedown.stop>
                                    <el-icon><CopyDocument /></el-icon>
                                  </el-button>
                                </div>
                              </div>
                            </template>
                            <el-empty v-else description="拖拽组件到此处" />
                          </div>
                        </el-col>
                      </el-row>
                    </template>
                    <template v-else-if="item.type === 'table'">
                      <el-table v-bind="item.props" :data="[{ id: 1 }]">
                        <el-table-column v-for="(col, colIndex) in item.children" :key="col.id || colIndex" :label="'列 ' + (colIndex + 1)">
                          <template #default>
                            <div class="layout-drop-area table-drop-area" @dragover.prevent @drop="handleLayoutDrop($event, col)">
                              <template v-if="col.children && col.children.length > 0">
                                <div
                                  v-for="(child, childIndex) in col.children"
                                  :key="child.id || childIndex"
                                  class="form-item"
                                  :class="{ selected: currentComponent?.id === child.id }"
                                  @click.stop="selectComponent(child)"
                                  :data-component-id="child.id"
                                >
                                  <el-form-item :label="child.label">
                                    <template v-if="child.type === 'date'">
                                      <el-date-picker
                                        v-model="formData[child.field]"
                                        type="date"
                                        v-bind="getFilteredProps(child.props)"
                                        :format="child.props.format || 'YYYY-MM-DD'"
                                        :value-format="child.props.valueFormat || 'YYYY-MM-DD'"
                                      />
                                    </template>
                                    <template v-else-if="child.type === 'datetime'">
                                      <el-date-picker
                                        v-model="formData[child.field]"
                                        type="datetime"
                                        v-bind="getFilteredProps(child.props)"
                                        :format="child.props.format || 'YYYY-MM-DD HH:mm:ss'"
                                        :value-format="child.props.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
                                      />
                                    </template>
                                    <template v-else-if="child.type === 'time'">
                                      <el-time-picker
                                        v-model="formData[child.field]"
                                        v-bind="getFilteredProps(child.props)"
                                        :format="child.props.format || 'HH:mm:ss'"
                                        :value-format="child.props.valueFormat || 'HH:mm:ss'"
                                      />
                                    </template>
                                    <template v-else>
                                      <component
                                        :is="getComponentByType(child.type)"
                                        v-model="formData[child.field]"
                                        v-bind="getFilteredProps(child.props)"
                                      />
                                    </template>
                                  </el-form-item>
                                  <div v-if="currentComponent?.id === child.id" class="form-item-actions">
                                    <el-button
                                      type="danger"
                                      size="small"
                                      data-action="delete-root-component"
                                      @click.stop="onDeleteClick"
                                      @mousedown.stop
                                    >
                                      <el-icon @click.stop="onDeleteClick"><Delete /></el-icon>
                                    </el-button>
                                    <el-button type="primary" size="small" @click.stop="handleCopyComponent" @mousedown.stop>
                                      <el-icon><CopyDocument /></el-icon>
                                    </el-button>
                                  </div>
                                </div>
                              </template>
                              <el-empty v-else description="拖拽组件到此处" />
                            </div>
                          </template>
                        </el-table-column>
                      </el-table>
                    </template>
                    <template v-else-if="item.type === 'tabs'">
                      <el-tabs v-bind="item.props">
                        <el-tab-pane
                          v-for="(tab, tabIndex) in item.children"
                          :key="tab.name || tab.id || tabIndex"
                          :label="tab.label"
                          :name="tab.name"
                        >
                          <div class="layout-drop-area" @dragover.prevent @drop="handleLayoutDrop($event, tab)">
                            <template v-if="tab.children && tab.children.length > 0">
                              <div
                                v-for="(child, childIndex) in tab.children"
                                :key="child.id || childIndex"
                                class="form-item"
                                :class="{ selected: currentComponent?.id === child.id }"
                                @click.stop="selectComponent(child)"
                                :data-component-id="child.id"
                              >
                                <el-form-item :label="child.label">
                                  <template v-if="child.type === 'date'">
                                    <el-date-picker
                                      v-model="formData[child.field]"
                                      type="date"
                                      v-bind="getFilteredProps(child.props)"
                                      :format="child.props.format || 'YYYY-MM-DD'"
                                      :value-format="child.props.valueFormat || 'YYYY-MM-DD'"
                                    />
                                  </template>
                                  <template v-else-if="child.type === 'datetime'">
                                    <el-date-picker
                                      v-model="formData[child.field]"
                                      type="datetime"
                                      v-bind="getFilteredProps(child.props)"
                                      :format="child.props.format || 'YYYY-MM-DD HH:mm:ss'"
                                      :value-format="child.props.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
                                    />
                                  </template>
                                  <template v-else-if="child.type === 'time'">
                                    <el-time-picker
                                      v-model="formData[child.field]"
                                      v-bind="getFilteredProps(child.props)"
                                      :format="child.props.format || 'HH:mm:ss'"
                                      :value-format="child.props.valueFormat || 'HH:mm:ss'"
                                    />
                                  </template>
                                  <template v-else>
                                    <component
                                      :is="getComponentByType(child.type)"
                                      v-model="formData[child.field]"
                                      v-bind="getFilteredProps(child.props)"
                                    />
                                  </template>
                                </el-form-item>
                                <div v-if="currentComponent?.id === child.id" class="form-item-actions">
                                  <el-button
                                    type="danger"
                                    size="small"
                                    data-action="delete-root-component"
                                    @click.stop="onDeleteClick"
                                    @mousedown.stop
                                  >
                                    <el-icon @click.stop="onDeleteClick"><Delete /></el-icon>
                                  </el-button>
                                  <el-button type="primary" size="small" @click.stop="handleCopyComponent" @mousedown.stop>
                                    <el-icon><CopyDocument /></el-icon>
                                  </el-button>
                                </div>
                              </div>
                            </template>
                            <el-empty v-else description="拖拽组件到此处" />
                          </div>
                        </el-tab-pane>
                      </el-tabs>
                    </template>
                    <template v-else-if="item.type === 'card'">
                      <el-card v-bind="item.props">
                        <div class="layout-drop-area" @dragover.prevent @drop="handleLayoutDrop($event, item)">
                          <template v-if="item.children && item.children.length > 0">
                            <div
                              v-for="(child, childIndex) in item.children"
                              :key="child.id || childIndex"
                              class="form-item"
                              :class="{ selected: currentComponent?.id === child.id }"
                              @click.stop="selectComponent(child)"
                              :data-component-id="child.id"
                            >
                              <el-form-item :label="child.label">
                                <template v-if="child.type === 'date'">
                                  <el-date-picker
                                    v-model="formData[child.field]"
                                    type="date"
                                    v-bind="getFilteredProps(child.props)"
                                    :format="child.props.format || 'YYYY-MM-DD'"
                                    :value-format="child.props.valueFormat || 'YYYY-MM-DD'"
                                  />
                                </template>
                                <template v-else-if="child.type === 'datetime'">
                                  <el-date-picker
                                    v-model="formData[child.field]"
                                    type="datetime"
                                    v-bind="getFilteredProps(child.props)"
                                    :format="child.props.format || 'YYYY-MM-DD HH:mm:ss'"
                                    :value-format="child.props.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
                                  />
                                </template>
                                <template v-else-if="child.type === 'time'">
                                  <el-time-picker
                                    v-model="formData[child.field]"
                                    v-bind="getFilteredProps(child.props)"
                                    :format="child.props.format || 'HH:mm:ss'"
                                    :value-format="child.props.valueFormat || 'HH:mm:ss'"
                                  />
                                </template>
                                <template v-else>
                                  <component
                                    :is="getComponentByType(child.type)"
                                    v-model="formData[child.field]"
                                    v-bind="getFilteredProps(child.props)"
                                  />
                                </template>
                              </el-form-item>
                              <div v-if="currentComponent?.id === child.id" class="form-item-actions">
                                <el-button type="danger" size="small" data-action="delete-root-component" @click.stop="onDeleteClick" @mousedown.stop>
                                  <el-icon @click.stop="onDeleteClick"><Delete /></el-icon>
                                </el-button>
                                <el-button type="primary" size="small" @click.stop="handleCopyComponent" @mousedown.stop>
                                  <el-icon><CopyDocument /></el-icon>
                                </el-button>
                              </div>
                            </div>
                          </template>
                          <el-empty v-else description="拖拽组件到此处" />
                        </div>
                      </el-card>
                    </template>
                    <template v-else-if="item.type === 'image-upload'">
                      <el-upload v-model:file-list="formData[item.field]" v-bind="item.props">
                        <el-icon><Plus /></el-icon>
                      </el-upload>
                    </template>
                    <template v-else-if="item.type === 'file-upload'">
                      <el-upload v-model:file-list="formData[item.field]" v-bind="item.props">
                        <el-button type="primary">点击上传</el-button>
                      </el-upload>
                    </template>
                    <template v-else-if="item.type === 'rate'">
                      <el-rate v-model="formData[item.field]" v-bind="item.props" />
                    </template>
                    <template v-else-if="item.type === 'color-picker'">
                      <el-color-picker v-model="formData[item.field]" v-bind="item.props" />
                    </template>
                    <template v-else-if="item.type === 'slider'">
                      <el-slider v-model="formData[item.field]" v-bind="item.props" />
                    </template>
                    <template v-else-if="item.type === 'button'">
                      <el-button v-bind="item.props" @click="handleButtonClick(item.field)">{{ item.props.text }}</el-button>
                    </template>
                    <template v-else-if="item.type === 'divider'">
                      <el-divider v-bind="item.props">{{ item.props.text }}</el-divider>
                    </template>
                    <template v-else-if="item.type === 'dept-select'">
                      <el-tree-select
                        v-model="formData[item.field]"
                        :data="deptOptions"
                        :props="{ label: 'deptName', value: 'deptId' }"
                        v-bind="item.props"
                        @change="handleDeptChange"
                      />
                    </template>
                    <template v-else-if="item.type === 'user-select'">
                      <el-select v-model="formData[item.field]" v-bind="item.props" @change="handleUserChange">
                        <el-option v-for="user in userOptions" :key="user.userId" :label="user.nickName" :value="user.userId" />
                      </el-select>
                    </template>
                    <template v-else-if="item.type === 'switch'">
                      <el-switch v-model="formData[item.field]" v-bind="item.props" @change="(value) => handleFieldChange(value, item)" />
                    </template>
                    <template v-else-if="item.type === 'editor'">
                      <RichEditor
                        v-model="formData[item.field]"
                        :placeholder="item.props.placeholder"
                        :height="item.props.height"
                        :min-height="item.props.minHeight"
                        @change="handleComponentChange(item)"
                      />
                    </template>
                  </el-form-item>
                  <div v-if="currentComponent?.id === item.id" class="form-item-actions">
                    <el-button type="danger" size="small" data-action="delete-root-component" @click.stop="onDeleteClick" @mousedown.stop>
                      <el-icon @click.stop="onDeleteClick"><Delete /></el-icon>
                    </el-button>
                    <el-button type="primary" size="small" @click.stop="handleCopyComponent" @mousedown.stop>
                      <el-icon><CopyDocument /></el-icon>
                    </el-button>
                  </div>
                </div>
              </el-form>
            </div>
          </el-scrollbar>
        </div>
      </div>

      <!-- 右侧属性配置面板 -->
      <div class="properties-panel">
        <div class="panel-header">
          <h3>属性配置</h3>
          <el-button v-if="currentComponent" type="danger" size="small" data-action="delete-root-component" @click="onDeleteClick">
            <el-icon @click.stop="onDeleteClick"><Delete /></el-icon>删除组件
          </el-button>
        </div>
        <div class="properties-content">
          <el-scrollbar>
            <el-tabs v-model="activePropertiesTab">
              <el-tab-pane label="字段属性" name="field">
                <el-form v-if="currentComponent">
                  <!-- 添加组件类型显示 -->
                  <el-form-item label="组件类型">
                    <div class="component-type-tags">
                      <el-tag size="small" type="info" class="type-tag">{{ currentComponent.type }}</el-tag>
                      <el-tag v-if="currentComponent.dictType" size="small" type="success" class="dict-tag">
                        字典: {{ currentComponent.dictType }}
                      </el-tag>
                    </div>
                  </el-form-item>
                  <el-form-item label="组件ID">
                    <el-input v-model="currentComponent.id" @input="updateComponent" />
                  </el-form-item>
                  <el-form-item label="标签文本">
                    <el-input v-model="currentComponent.label" @input="updateComponent" />
                  </el-form-item>
                  <el-form-item label="字段名">
                    <el-input v-model="currentComponent.field" @input="updateComponent" />
                  </el-form-item>
                  <el-form-item label="是否必填">
                    <el-switch v-model="currentComponent.required" @input="updateComponent" />
                  </el-form-item>

                  <!-- 选项编辑区域，紧跟在选项标题下面 -->
                  <el-form-item v-if="['select', 'radio', 'checkbox'].includes(currentComponent.type)" label="选项" label-position="top">
                    <!-- 数据来源选择 -->
                    <el-select v-model="currentComponent.props.dataSource" placeholder="请选择数据来源" style="margin-bottom: 10px; width: 100%">
                      <el-option label="静态数据" value="static" />
                      <el-option label="数据字典" value="dict" />
                    </el-select>

                    <!-- 静态数据编辑 -->
                    <template v-if="currentComponent.props.dataSource === 'static'">
                      <div
                        v-for="(opt, idx) in currentComponent.props.options"
                        :key="idx"
                        style="display: flex; align-items: center; margin-bottom: 4px"
                      >
                        <el-input v-model="opt.label" size="small" placeholder="标签" style="width: 100px; margin-right: 8px" />
                        <el-input v-model="opt.value" size="small" placeholder="值" style="width: 100px; margin-right: 8px" />
                        <el-button
                          icon="Delete"
                          size="small"
                          type="danger"
                          @click="currentComponent.props.options.splice(idx, 1)"
                          style="margin-left: 0; margin-right: 4px"
                          circle
                        />
                      </div>
                      <el-button
                        size="small"
                        icon="Plus"
                        type="primary"
                        style="margin-top: 8px; width: 100%"
                        @click="currentComponent.props.options.push({ label: '', value: '' })"
                        >添加选项</el-button
                      >
                    </template>

                    <!-- 数据字典选择 -->
                    <template v-if="currentComponent.props.dataSource === 'dict'">
                      <el-select
                        v-model="currentComponent.props.dictType"
                        placeholder="请选择字典类型"
                        @change="handleDictTypeChange"
                        style="width: 100%"
                      >
                        <el-option v-for="item in dictTypeOptions" :key="item.dictType" :label="item.dictName" :value="item.dictType" />
                      </el-select>
                      <el-alert type="info" show-icon :closable="false" style="margin-top: 10px">
                        选项由字典【{{ currentComponent.props.dictType }}】自动驱动，无法手动编辑
                      </el-alert>
                      <el-table :data="currentComponent.props.options" size="small" style="margin-top: 8px">
                        <el-table-column prop="label" label="标签" />
                        <el-table-column prop="value" label="值" />
                      </el-table>
                    </template>
                  </el-form-item>

                  <!-- 修改为可编辑的数据库字段信息 -->
                  <el-divider content-position="left">数据库字段信息</el-divider>
                  <el-form-item label="所属表">
                    <el-select v-if="currentSubTableColumn" v-model="currentComponent.subTableName" class="full-width" disabled>
                      <el-option v-for="table in tables" :key="table.tableName" :label="table.tableName" :value="table.tableName" />
                    </el-select>
                    <el-select
                      v-else
                      v-model="currentComponent.dbInfo.tableName"
                      placeholder="请选择数据表"
                      @change="(val) => listTableColumns(val)"
                      class="full-width"
                    >
                      <el-option v-for="table in tables" :key="table.tableName" :label="table.tableName" :value="table.tableName" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="字段名">
                    <el-select
                      v-if="currentSubTableColumn"
                      v-model="currentSubTableColumn.dbInfo.columnName"
                      placeholder="请选择字段"
                      @change="handleColumnChange"
                      :disabled="!currentComponent.subTableName"
                      class="full-width"
                    >
                      <el-option
                        v-for="column in currentTableColumns"
                        :key="column.columnName"
                        :label="column.columnName"
                        :value="column.columnName"
                      />
                    </el-select>
                    <el-select
                      v-else
                      v-model="currentComponent.dbInfo.columnName"
                      placeholder="请选择字段"
                      @change="handleColumnChange"
                      :disabled="!currentComponent.dbInfo.tableName"
                      class="full-width"
                    >
                      <el-option
                        v-for="column in currentTableColumns"
                        :key="column.columnName"
                        :label="column.columnName"
                        :value="column.columnName"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="字段类型">
                    <el-input v-if="currentSubTableColumn" v-model="currentSubTableColumn.dbInfo.columnType" @input="updateComponent" />
                    <el-input v-else v-model="currentComponent.dbInfo.columnType" @input="updateComponent" />
                  </el-form-item>
                  <el-form-item label="字段说明">
                    <el-input v-if="currentSubTableColumn" v-model="currentSubTableColumn.dbInfo.columnComment" @input="updateComponent" />
                    <el-input v-else v-model="currentComponent.dbInfo.columnComment" @input="updateComponent" />
                  </el-form-item>
                  <el-form-item label="是否可空">
                    <el-select v-if="currentSubTableColumn" v-model="currentSubTableColumn.dbInfo.isNullable" class="full-width">
                      <el-option label="是" value="YES" />
                      <el-option label="否" value="NO" />
                    </el-select>
                    <el-select v-else v-model="currentComponent.dbInfo.isNullable" class="full-width">
                      <el-option label="是" value="YES" />
                      <el-option label="否" value="NO" />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="currentComponent?.type === 'subtable'" label="子表配置">
                    <el-button type="primary" size="small" @click="handleSelectSubTable"> 选择子表 </el-button>
                  </el-form-item>
                </el-form>
              </el-tab-pane>
              <el-tab-pane label="表单属性" name="form">
                <el-form label-position="top">
                  <el-form-item label="表单名称">
                    <el-input v-model="form.formName" placeholder="请输入表单名称" />
                  </el-form-item>
                  <el-form-item label="表单描述">
                    <el-input v-model="form.formDesc" placeholder="请输入表单描述" />
                  </el-form-item>
                  <el-form-item label="标签宽度">
                    <el-select v-model="deviceType">
                      <el-option label="PC端" value="pc" />
                      <el-option label="移动端" value="mobile" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="标签位置">
                    <el-select v-model="labelPosition">
                      <el-option label="左对齐" value="left" />
                      <el-option label="右对齐" value="right" />
                      <el-option label="顶部对齐" value="top" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="表单尺寸">
                    <el-select v-model="formSize">
                      <el-option label="大" value="large" />
                      <el-option label="默认" value="default" />
                      <el-option label="小" value="small" />
                    </el-select>
                  </el-form-item>
                </el-form>
              </el-tab-pane>
            </el-tabs>
          </el-scrollbar>
        </div>
      </div>

      <!-- 预览对话框 -->
      <el-dialog
        v-model="previewVisible"
        title="表单预览"
        width="95%"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        :fullscreen="false"
        top="2vh"
        destroy-on-close
      >
        <form-preview
          v-model="previewVisible"
          :form-content="
            JSON.stringify({
              formConfig: {
                labelWidth: deviceType === 'pc' ? '100px' : '80px',
                labelPosition: 'right',
                size: 'default',
                formName: form.formName,
                formDesc: form.formDesc
              },
              formComponents: formComponents,
              masterSubTableData: masterSubTableData
            })
          "
        />
      </el-dialog>

      <!-- 代码生成对话框 -->
      <el-dialog v-model="codeVisible" title="生成代码" width="60%">
        <div class="dialog-scroll-content">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="Vue 模板" name="template">
              <pre class="code-block">{{ vueTemplate }}</pre>
            </el-tab-pane>
            <el-tab-pane label="Vue 脚本" name="script">
              <pre class="code-block">{{ vueScript }}</pre>
            </el-tab-pane>
            <el-tab-pane label="Vue 样式" name="style">
              <pre class="code-block">{{ vueStyle }}</pre>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-dialog>

      <!-- 保存表单对话框 -->
      <el-dialog v-model="dialog.visible" :title="dialog.title" width="500px" append-to-body>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
          <el-form-item label="表单名称" prop="formName">
            <el-input v-model="form.formName" placeholder="请输入表单名称" />
          </el-form-item>
          <el-form-item label="表单描述" prop="formDesc">
            <el-input v-model="form.formDesc" placeholder="请输入表单描述" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button type="primary" @click="submitForm">确 定</el-button>
            <el-button @click="dialog.visible = false">取 消</el-button>
          </div>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, markRaw, watch, onMounted } from 'vue';

// 定义组件名称
defineOptions({
  name: 'FormDesigner'
});
import type { PropType } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Document,
  Edit,
  Select,
  Check,
  Plus,
  Delete,
  CopyDocument,
  View,
  Download,
  Close,
  Grid,
  Menu,
  Collection,
  Tickets,
  OfficeBuilding,
  User
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { list, getDataNames } from '@/api/online';
import { TableQuery, TableVO, OnlFieldVo } from '@/api/online/types';
import { getTableColumns } from '@/api/tool/gen';
import { addNform, updateNform } from '@/api/workflow/nform';
import { FormComponent as ImportedFormComponent, SubTableColumn, FormConfig as ImportedFormConfig, FormTableInfo } from '@/types/form';
import {
  ElInput,
  ElInputNumber,
  ElSelect,
  ElRadio,
  ElCheckbox,
  ElSwitch,
  ElDatePicker,
  ElTimePicker,
  ElRow,
  ElCol,
  ElTable,
  ElTabs,
  ElTabPane,
  ElCard,
  ElUpload,
  ElRate,
  ElColorPicker,
  ElSlider,
  ElButton,
  ElDivider
} from 'element-plus';
import { listDept } from '@/api/system/dept';
import { listUser } from '@/api/system/user';
import { optionselect } from '@/api/system/dict/type';
import type { DictTypeVO } from '@/api/system/dict/type/types';
import type { UserVO } from '@/api/system/user/types';
import { getDicts } from '@/api/system/dict/data';
import { useDict } from '@/utils/dict';
import type { DictDataVO } from '@/api/system/dict/data/types';
import FormPreview from './FormPreview.vue';
import RichEditor from './components/RichEditor.vue';
import PopupTable from './components/PopupTable.vue';

const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const dateRange = ref<[DateModelType, DateModelType]>(['', '']);
const tableSelectorRef = ref();

interface DictOption {
  label: string;
  value: string | number;
}

// 添加字典选项的响应式引用
const dictOptions = ref<Record<string, DictOption[]>>({});

// 获取字典选项
const getDictOptions = async (dictType: string): Promise<DictOption[]> => {
  if (!dictType) return [];

  if (dictCache.value[dictType]) {
    return dictCache.value[dictType];
  }

  try {
    const res = await getDicts(dictType);
    if (res.code === 200) {
      const options = res.data.map((item: any) => ({
        label: item.dictLabel,
        value: item.dictValue
      }));
      dictCache.value[dictType] = options;
      return options;
    }
    return [];
  } catch (error) {
    console.error('获取字典数据失败:', error);
    return [];
  }
};

// 定义组件属性
const props = defineProps({
  formData: {
    type: Object as PropType<FormState>,
    default: () => ({
      formName: '',
      formDesc: '',
      formContent: ''
    })
  },
  title: {
    type: String,
    default: '表单设计器'
  }
});

// 定义组件事件
const emit = defineEmits(['save', 'close']);

// 添加原始数据保存用于修改检测
const originalFormData = ref<any>(null);
const originalFormComponents = ref<any[]>([]);

// 定义表单数据类型
interface FormData {
  [key: string]: any;
}

// 定义组件类型
interface Component {
  label: string;
  type: string;
  props: Record<string, any>;
  icon: any;
}

// 定义表单组件类型
interface FormComponent {
  id: string;
  type: string;
  label: string;
  field: string;
  props: Record<string, any>;
  required?: boolean;
  isMaster?: boolean;
  masterField?: string;
  subTableName?: string;
  subTableColumns?: SubTableColumn[];
  dbInfo: {
    tableName: string;
    columnName: string;
    columnType: string;
    columnComment: string;
    isNullable: string;
  };
  children?: FormComponent[]; // 添加children属性
}

// 定义表单配置类型
interface FormConfig {
  labelWidth: string;
  labelPosition: string;
  size: string;
  formName: string;
  formDesc: string;
}

// 定义表单内容类型
interface FormContent {
  formConfig: FormConfig;
  formComponents: FormComponent[];
  masterSubTableData: Record<string, any[]>;
}

// 定义表单数据类型
interface FormState {
  formId?: string;
  formName: string;
  formDesc: string;
  formContent: string;
}

// 定义对话框状态
const dialog = reactive({
  visible: false,
  title: ''
});

// 定义表单数据
const form = reactive<FormState>({
  formName: '',
  formDesc: '',
  formContent: ''
});

// 定义表单值数据
const formData = ref<Record<string, any>>({});

// 初始化表单数据
const initFormData = () => {
  formData.value = {};
  formComponents.value.forEach((component: FormComponent) => {
    switch (component.type) {
      case 'checkbox':
        formData.value[component.field] = [];
        break;
      case 'select':
      case 'radio':
        // 如果是字典类型，确保字典数据已加载
        if (component.props.dataSource === 'dict' && component.props.dictType) {
          const dictData = dictOptions.value[component.props.dictType] || [];
          // 如果有默认值，确保它在字典数据中存在
          if (component.props.defaultValue && dictData.length > 0) {
            formData.value[component.field] = component.props.defaultValue;
          } else {
            formData.value[component.field] = '';
          }
        } else {
          formData.value[component.field] = '';
        }
        break;
      case 'switch':
        formData.value[component.field] = false;
        break;
      case 'image-upload':
      case 'file-upload':
        formData.value[component.field] = [];
        break;
      case 'rate':
      case 'slider':
        formData.value[component.field] = 0;
        break;
      case 'color-picker':
        formData.value[component.field] = '#409EFF';
        break;
      case 'dept-select':
      case 'user-select':
        formData.value[component.field] = component.props.multiple ? [] : '';
        break;
      default:
        formData.value[component.field] = '';
    }
  });
};

// 重置表单
const reset = () => {
  form.formName = '';
  form.formDesc = '';
  form.formContent = '';
};

// 修改加载表单数据的方法
const loadFormData = async () => {
  if (props.formData) {
    // 设置表单名称和描述
    form.formName = props.formData.formName || '';
    form.formDesc = props.formData.formDesc || '';
    form.formId = props.formData.formId;

    // 如果有表单内容，则解析并加载
    if (props.formData.formContent) {
      try {
        const content = JSON.parse(props.formData.formContent) as FormContent;
        formComponents.value = content.formComponents || [];
        masterSubTableData.value = content.masterSubTableData || {};

        // 生成表单时：确保每个子表至少有一条空记录
        formComponents.value.forEach((comp: any) => {
          if (comp.type === 'subtable') {
            const key = comp.field;
            if (!Array.isArray(masterSubTableData.value[key])) {
              masterSubTableData.value[key] = [];
            }
            if (masterSubTableData.value[key].length === 0) {
              const row: Record<string, any> = {};
              comp.subTableColumns?.forEach((c: any) => {
                row[c.field] = '';
              });
              masterSubTableData.value[key].push(row);
            }
          }
        });

        // 收集所有需要加载的字典类型
        const dictTypes = new Set<string>();
        formComponents.value.forEach((component) => {
          if (['select', 'radio', 'checkbox'].includes(component.type) && component.props.dataSource === 'dict' && component.props.dictType) {
            dictTypes.add(component.props.dictType);
          }
        });

        // 并行加载所有字典数据
        await Promise.all(Array.from(dictTypes).map((dictType) => loadDictData(dictType)));

        if (content.formConfig) {
          deviceType.value = content.formConfig.labelWidth === '100px' ? 'pc' : 'mobile';
          // 确保表单名称和描述与表单配置同步
          if (content.formConfig.formName) {
            form.formName = content.formConfig.formName;
          }
          if (content.formConfig.formDesc) {
            form.formDesc = content.formConfig.formDesc;
          }
        }

        // 初始化表单数据
        initFormData();

        // 在所有数据处理完成后，保存原始数据用于修改检测
        originalFormData.value = {
          formName: form.formName,
          formDesc: form.formDesc,
          formContent: props.formData.formContent
        };
        originalFormComponents.value = JSON.parse(JSON.stringify(formComponents.value));
      } catch (error) {
        console.error('解析表单内容失败:', error);
        ElMessage.error('解析表单内容失败');
      }
    } else {
      // 新建表单时，清空原始数据
      originalFormData.value = null;
      originalFormComponents.value = [];
    }
  }
};

const deviceType = ref('pc');
const previewVisible = ref(false);
const codeVisible = ref(false);
const activeTab = ref('template');

// 基础组件列表
const basicComponents = ref([
  {
    type: 'input',
    label: '输入框',
    icon: 'Edit',
    component: markRaw(ElInput),
    props: {
      placeholder: '请输入',
      clearable: true
    }
  },
  {
    type: 'textarea',
    label: '多行输入',
    icon: 'Document',
    component: markRaw(ElInput),
    props: {
      type: 'textarea',
      placeholder: '请输入多行文本',
      rows: 4,
      clearable: true
    }
  },
  {
    type: 'image-upload',
    label: '图片上传',
    icon: 'Picture',
    component: markRaw(ElUpload),
    props: {
      action: '/api/upload',
      listType: 'picture-card',
      accept: 'image/*',
      multiple: true,
      limit: 5
    }
  },
  {
    type: 'file-upload',
    label: '文件上传',
    icon: 'Upload',
    component: markRaw(ElUpload),
    props: {
      action: '/api/upload',
      multiple: true,
      limit: 5
    }
  },
  {
    type: 'rate',
    label: '评分',
    icon: 'Star',
    component: markRaw(ElRate),
    props: {
      max: 5,
      allowHalf: true
    }
  },
  {
    type: 'color-picker',
    label: '颜色选择器',
    icon: 'Brush',
    component: markRaw(ElColorPicker),
    props: {
      showAlpha: true
    }
  },
  {
    type: 'slider',
    label: '滑块',
    icon: 'Operation',
    component: markRaw(ElSlider),
    props: {
      min: 0,
      max: 100,
      step: 1
    }
  },
  {
    type: 'button',
    label: '按钮',
    icon: 'Select',
    component: markRaw(ElButton),
    props: {
      type: 'primary',
      text: '按钮'
    }
  },
  {
    type: 'divider',
    label: '分隔线',
    icon: 'Minus',
    component: markRaw(ElDivider),
    props: {
      contentPosition: 'center',
      text: '分隔线'
    }
  },
  {
    type: 'number',
    label: '数字输入框',
    icon: 'Edit',
    component: markRaw(ElInputNumber),
    props: {
      placeholder: '请输入数字',
      clearable: true
    }
  },
  {
    type: 'select',
    label: '下拉选择',
    icon: 'Select',
    component: markRaw(ElSelect),
    props: {
      placeholder: '请选择',
      clearable: true,
      dataSource: 'static',
      dictType: '',
      options: [
        { label: '选项1', value: '1' },
        { label: '选项2', value: '2' }
      ]
    }
  },
  {
    type: 'radio',
    label: '单选框组',
    icon: 'Operation',
    props: {
      dataSource: 'static',
      dictType: '',
      options: [
        { label: '选项1', value: '1' },
        { label: '选项2', value: '2' }
      ]
    }
  },
  {
    type: 'checkbox',
    label: '多选框组',
    icon: 'Operation',
    props: {
      dataSource: 'static',
      dictType: '',
      options: [
        { label: '选项1', value: '1' },
        { label: '选项2', value: '2' }
      ]
    }
  },
  {
    type: 'switch',
    label: '开关',
    icon: 'Select',
    component: markRaw(ElSwitch),
    props: {
      activeText: '是',
      inactiveText: '否',
      activeValue: true,
      inactiveValue: false
    }
  },
  {
    type: 'date',
    label: '日期选择器',
    icon: 'Calendar',
    component: markRaw(ElDatePicker),
    props: {
      type: 'date',
      placeholder: '请选择日期',
      clearable: true,
      format: 'YYYY-MM-DD',
      valueFormat: 'YYYY-MM-DD'
    }
  },
  {
    type: 'datetime',
    label: '日期时间选择器',
    icon: 'Calendar',
    component: markRaw(ElDatePicker),
    props: {
      type: 'datetime',
      placeholder: '请选择日期时间',
      clearable: true,
      format: 'YYYY-MM-DD HH:mm:ss',
      valueFormat: 'YYYY-MM-DD HH:mm:ss'
    }
  },
  {
    type: 'time',
    label: '时间选择器',
    icon: 'Timer',
    component: markRaw(ElTimePicker),
    props: {
      placeholder: '请选择时间',
      clearable: true,
      format: 'HH:mm:ss',
      valueFormat: 'HH:mm:ss'
    }
  },
  {
    type: 'editor',
    label: '富文本编辑器',
    icon: 'Document',
    component: markRaw(RichEditor),
    props: {
      placeholder: '请输入内容',
      height: 400,
      minHeight: 200
    }
  }
]);

// 布局组件列表
const layoutComponents = ref([
  {
    label: '栅格布局',
    type: 'grid',
    icon: markRaw(Grid),
    props: {
      gutter: 20,
      justify: 'start',
      align: 'top'
    },
    children: [
      {
        type: 'col',
        span: 12,
        children: []
      },
      {
        type: 'col',
        span: 12,
        children: []
      }
    ]
  },
  {
    label: '表格布局',
    type: 'table',
    icon: markRaw(Menu),
    props: {
      border: true,
      stripe: true
    },
    children: [
      {
        type: 'column',
        children: []
      },
      {
        type: 'column',
        children: []
      }
    ]
  },
  {
    label: '标签页',
    type: 'tabs',
    icon: markRaw(Tickets),
    props: {
      type: 'card'
    },
    children: [
      {
        type: 'tab-pane',
        label: '标签页1',
        name: 'tab1',
        children: []
      },
      {
        type: 'tab-pane',
        label: '标签页2',
        name: 'tab2',
        children: []
      }
    ]
  },
  {
    label: '卡片',
    type: 'card',
    icon: markRaw(Collection),
    props: {
      header: '卡片标题',
      shadow: 'hover'
    },
    children: []
  }
]);

// 高级组件列表
const advancedComponents = ref([
  {
    label: '子表',
    type: 'subtable',
    props: { placeholder: '子表' },
    icon: markRaw(Plus)
  },
  {
    label: '部门选择',
    type: 'dept-select',
    props: {
      placeholder: '请选择部门',
      multiple: false,
      clearable: true
    },
    icon: markRaw(OfficeBuilding)
  },
  {
    label: '人员选择',
    type: 'user-select',
    props: {
      placeholder: '请选择人员',
      multiple: false,
      clearable: true
    },
    icon: markRaw(User)
  },
  {
    label: '弹窗选择',
    type: 'popup',
    props: {
      placeholder: '请选择',
      dictType: '',
      dictFields: {},
      mapping: {}
    },
    icon: markRaw(Plus)
  }
]);

const formComponents = ref<FormComponent[]>([]);
const currentComponent = ref<FormComponent | null>(null);

const tables = ref<TableVO[]>([]);
const currentTable = ref<TableVO | null>(null);
const currentTableColumns = ref<any[]>([]);
const loading = ref(false);

// 添加主子表数据
const masterSubTableData = ref<Record<string, any[]>>({});

const queryParams = ref<any>({
  pageNum: 1,
  pageSize: 10,
  tableName: '',
  tableComment: '',
  dataName: '',
  queryType: 'eq',
  queryFields: [],
  queryConditions: [],
  sortFields: []
});

const dataNameList = ref<string[]>([]);
const total = ref<number>(0);

// 添加当前选中的子表列
const currentSubTableColumn = ref<SubTableColumn | null>(null);

const listTableColumns = async (tableName: string) => {
  try {
    const res = await getTableColumns(tableName);
    if (res) {
      currentTableColumns.value = res;
    } else {
      currentTableColumns.value = [];
    }
  } catch (error) {
    console.error('获取表字段列表失败:', error);
    ElMessage.error('获取表字段列表失败');
  }
};

const handleDragStart = (e: DragEvent, item: any) => {
  e.dataTransfer?.setData('text/plain', JSON.stringify(item));
};

const handleSubTableDrop = (e: DragEvent, subTableComponent: FormComponent) => {
  const data = e.dataTransfer?.getData('text/plain');
  if (!data) return;

  const component = JSON.parse(data);
  const newComponent = {
    ...component,
    id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    field: `field_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    required: false,
    dbInfo: {
      tableName: '',
      columnName: '',
      columnType: '',
      columnComment: '',
      isNullable: 'YES'
    },
    isMaster: false,
    masterField: '',
    subTableName: '',
    subTableColumns: []
  };

  // 检查是否已经存在相同的组件
  const existingColumn = subTableComponent.subTableColumns?.find((col) => col.type === newComponent.type && col.field === newComponent.field);

  if (existingColumn) {
    // 如果已存在，则选中该列
    currentComponent.value = subTableComponent;
    currentSubTableColumn.value = existingColumn;
    return;
  }

  // 将新组件添加到子表的列中
  if (!subTableComponent.subTableColumns) {
    subTableComponent.subTableColumns = [];
  }

  const newColumn: SubTableColumn = {
    id: newComponent.id,
    field: newComponent.field,
    label: newComponent.label,
    type: newComponent.type,
    props: newComponent.props,
    dbInfo: newComponent.dbInfo
  };

  subTableComponent.subTableColumns.push(newColumn);
  // 选中新添加的列
  currentComponent.value = subTableComponent;
  currentSubTableColumn.value = newColumn;

  // 初始化子表数据
  if (!masterSubTableData.value[subTableComponent.field]) {
    masterSubTableData.value[subTableComponent.field] = [];
  }
  // 若当前无行，则基于已有列创建一条默认空行
  if (masterSubTableData.value[subTableComponent.field].length === 0) {
    const row: Record<string, any> = {};
    subTableComponent.subTableColumns?.forEach((col) => {
      row[col.field] = '';
    });
    masterSubTableData.value[subTableComponent.field].push(row);
  }
};

const handleDrop = (e: DragEvent) => {
  e.preventDefault();
  const data = e.dataTransfer?.getData('text/plain');
  if (!data) return;

  const component = JSON.parse(data);
  const newComponent = {
    ...component,
    id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    field: `field_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    required: false,
    dbInfo: {
      tableName: '',
      columnName: '',
      columnType: '',
      columnComment: '',
      isNullable: 'YES'
    },
    isMaster: false,
    masterField: '',
    subTableName: '',
    subTableColumns: []
  };

  // 自动添加默认选项
  if (['select', 'radio', 'checkbox'].includes(newComponent.type)) {
    newComponent.props = {
      ...newComponent.props,
      options: [
        { label: '选项1', value: '1' },
        { label: '选项2', value: '2' }
      ]
    };
  }

  // 获取鼠标位置
  const dropY: number = e.clientY;
  let insertIndex: number = formComponents.value.length;

  // 遍历所有组件，找到插入位置
  for (let i = 0; i < formComponents.value.length; i++) {
    const element = document.querySelector(`[data-component-id="${formComponents.value[i].id}"]`);
    if (element) {
      const rect = element.getBoundingClientRect();
      if (dropY < rect.top + rect.height / 2) {
        insertIndex = i;
        break;
      }
    }
  }

  // 在指定位置插入新组件
  formComponents.value.splice(insertIndex, 0, newComponent);
  // 选中新添加的组件
  currentComponent.value = newComponent;
  currentSubTableColumn.value = null;
  formData.value[newComponent.field] = '';

  // 如果是子表，初始化数据
  if (newComponent.type === 'subtable') {
    masterSubTableData.value[newComponent.field] = [];
  }
};

const selectComponent = (component: FormComponent) => {
  // 如果点击的是当前选中的组件，则取消选中
  if (currentComponent.value?.id === component.id) {
    currentComponent.value = null;
  } else {
    currentComponent.value = component;
    // 如果是字典类型的组件，确保字典数据已加载
    if (['select', 'radio', 'checkbox'].includes(component.type) && component.props.dataSource === 'dict' && component.props.dictType) {
      loadDictData(component.props.dictType);
    }
  }
};

const handlePreview = () => {
  if (formComponents.value.length === 0) {
    ElMessage.warning('请先设计表单');
    return;
  }
  previewVisible.value = true;
};

const handleGenerateCode = () => {
  if (formComponents.value.length === 0) {
    ElMessage.warning('请先设计表单');
    return;
  }
  codeVisible.value = true;
};

const handleDeleteComponent = () => {
  if (!currentComponent.value) {
    return;
  }
  const targetId = currentComponent.value.id;

  // 查找组件所在的布局
  const findAndDeleteComponent = (components: FormComponent[]) => {
    for (let i = 0; i < components.length; i++) {
      const component = components[i];

      // 如果是布局组件，检查其子组件
      if (component.type === 'grid' || component.type === 'table' || component.type === 'tabs' || component.type === 'card') {
        if (component.children) {
          // 检查每个子组件
          for (let j = 0; j < component.children.length; j++) {
            const child = component.children[j];

            // 如果是布局组件的直接子组件
            if (child.id === currentComponent.value?.id) {
              // 删除子组件
              component.children.splice(j, 1);
              // 删除表单数据
              delete formData.value[currentComponent.value.field];
              // 清空当前选中的组件
              currentComponent.value = null;
              // 强制触发视图刷新
              component.children = [...component.children];
              formComponents.value = [...formComponents.value];
              return true;
            }

            // 如果是嵌套的子组件
            if (child.children) {
              if (findAndDeleteComponent(child.children)) {
                // 嵌套删除后，同样刷新视图
                child.children = [...child.children];
                formComponents.value = [...formComponents.value];
                return true;
              }
            }
          }
        }
      }

      // 如果是普通组件
      if (component.id === currentComponent.value.id) {
        // 删除组件
        components.splice(i, 1);
        // 删除表单数据
        delete formData.value[currentComponent.value.field];
        // 清空当前选中的组件
        currentComponent.value = null;
        // 强制触发视图刷新
        formComponents.value = [...formComponents.value];
        return true;
      }
    }
    return false;
  };

  // 从根组件开始查找并删除
  findAndDeleteComponent(formComponents.value);
  // 兜底：若仍残留，则从根层过滤一次并刷新
  if (targetId) {
    const beforeLen = formComponents.value.length;
    formComponents.value = formComponents.value.filter((c) => c && c.id !== targetId);
    if (formComponents.value.length !== beforeLen) {
      formComponents.value = [...formComponents.value];
    }
  }
};

const handleCopyComponent = () => {
  if (currentComponent.value) {
    const newComponent = {
      ...currentComponent.value,
      id: `component_${Date.now()}`,
      field: `field_${Date.now()}`
    };
    formComponents.value.push(newComponent);
    formData.value[newComponent.field] = '';
  }
};

const handleDatabaseConfig = () => {
  databaseConfigRef.value.visible = true;
};

const handleDesignerSave = async (formData: any) => {
  try {
    // 确保表单数据包含必要的字段
    if (!formData.formName || !formData.formDesc) {
      throw new Error('表单名称和描述不能为空');
    }

    // 如果是编辑模式，确保 formId 被包含在 formData 中
    if (props.formData?.formId) {
      formData.formId = props.formData.formId;
    }

    if (formData.formId) {
      // 编辑模式
      const res = await updateNform(formData);
      if (res.code === 200) {
        ElMessage.success('修改成功');
      } else {
        throw new Error(res.msg || '更新表单失败');
      }
    } else {
      // 新建模式
      const res = await addNform(formData);
      if (res.code === 200) {
        ElMessage.success('新增成功');
      } else {
        throw new Error(res.msg || '新增表单失败');
      }
    }
    designerVisible.value = false;
    getList();
  } catch (error) {
    console.error('保存表单失败:', error);
    ElMessage.error('保存表单失败: ' + (error as Error).message);
  }
};

/** 查询多数据源名称 */
const getDataNameList = async () => {
  try {
    const query: TableQuery = {
      pageNum: 1,
      pageSize: 100,
      tableName: '',
      tableComment: '',
      dataName: ''
    };
    const res = await list(query);
    if (res && Array.isArray(res.data)) {
      dataNameList.value = res.data;
    }
  } catch (error) {
    console.error('获取数据名称列表失败:', error);
  }
};

/** 查询表集合 */
const getList = async () => {
  loading.value = true;
  try {
    const query: TableQuery = {
      pageNum: queryParams.value.pageNum,
      pageSize: queryParams.value.pageSize,
      tableName: queryParams.value.tableName,
      tableComment: queryParams.value.tableComment,
      dataName: queryParams.value.dataName
    };
    const res = await list(query);
    if (res && res.rows) {
      tables.value = res.rows;
      total.value = res.total;
      if (tableSelectorRef.value) {
        tableSelectorRef.value.total = res.total;
        tableSelectorRef.value.tables = res.rows;
      }
    } else {
      tables.value = [];
      total.value = 0;
    }
  } catch (error) {
    console.error('获取列表失败:', error);
  } finally {
    loading.value = false;
  }
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置按钮操作 */
const resetQuery = () => {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    tableName: '',
    tableComment: '',
    dataName: '',
    queryType: 'list',
    queryFields: [],
    queryConditions: [],
    sortFields: [],
    columnName: '',
    dbInfo: {
      tableName: '',
      columnName: ''
    }
  };
  handleQuery();
};

const handleSelectTable = async () => {
  try {
    // 获取数据源列表
    await getDataNameList();

    // 设置默认数据源
    queryParams.value = {
      ...queryParams.value,
      tableName: 'master',
      pageNum: 1,
      pageSize: 10
    };

    // 获取表列表
    await getList();

    tableSelectorRef.value.visible = true;
  } catch (error) {
    console.error('获取数据表列表失败:', error);
    ElMessage.error('获取数据表列表失败');
  }
};

// 定义扩展的表格查询类型
interface ExtendedTableQuery extends TableQuery {
  queryType?: string;
  queryFields?: string[];
  queryConditions?: any[];
  sortFields?: any[];
  columnName?: string;
  dbInfo?: {
    tableName: string;
    columnName: string;
  };
}

// 定义表单配置
const formConfig = reactive<FormConfig>({
  labelWidth: deviceType.value === 'pc' ? '100px' : '80px',
  labelPosition: 'right',
  size: 'default',
  formName: '',
  formDesc: ''
});

// 修改生成表单方法
const handleGenerateForm = () => {
  if (formComponents.value.length === 0) {
    ElMessage.warning('请先设计表单组件');
    return;
  }

  const formContent = {
    formConfig,
    formComponents: formComponents.value,
    masterSubTableData: {}
  };

  formData.value.formContent = JSON.stringify(formContent);
  formData.value.formName = '自定义表单';
  formData.value.formDesc = '通过表单设计器生成的自定义表单';
  formData.value.formType = 'PC';

  ElMessageBox.confirm('确认生成表单？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 这里可以添加生成表单的逻辑
    ElMessage.success('表单生成成功');
    router.push('/workflow/form');
  });
};

const getComponentTemplate = (component: FormComponent): string => {
  switch (component.type) {
    case 'input':
      return `<el-input v-model="formData.${component.field}" placeholder="${component.props.placeholder}" />`;
    case 'textarea':
      return `<el-input type="textarea" v-model="formData.${component.field}" placeholder="${component.props.placeholder}" />`;
    case 'select':
      return `<el-select v-model="formData.${component.field}" placeholder="${component.props.placeholder || '请选择'}" v-bind="${JSON.stringify(component.props)}">
        <el-option
          v-for="option in ${component.props.dataSource === 'dict' ? `dictOptions['${component.props.dictType}']` : 'component.props.options'}"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>`;
    case 'checkbox':
      return `<el-checkbox-group v-model="formData.${component.field}" v-bind="${JSON.stringify(component.props)}">
        <el-checkbox
          v-for="option in ${component.props.dataSource === 'dict' ? `dictOptions['${component.props.dictType}']` : 'component.props.options'}"
          :key="option.value"
          :label="option.value"
        >
          {{ option.label }}
        </el-checkbox>
      </el-checkbox-group>`;
    case 'radio':
      return `<el-radio-group v-model="formData.${component.field}" v-bind="${JSON.stringify(component.props)}">
        <el-radio
          v-for="option in ${component.props.dataSource === 'dict' ? `dictOptions['${component.props.dictType}']` : 'component.props.options'}"
          :key="option.value"
          :label="option.value"
        >
          {{ option.label }}
        </el-radio>
      </el-radio-group>`;
    case 'date':
      return `<el-date-picker
        v-model="formData.${component.field}"
        type="date"
        v-bind="item.props"
        :format="item.props.format || 'YYYY-MM-DD'"
        :value-format="item.props.valueFormat || 'YYYY-MM-DD'"
      />`;
    case 'datetime':
      return `<el-date-picker
        v-model="formData.${component.field}"
        type="datetime"
        v-bind="item.props"
        :format="item.props.format || 'YYYY-MM-DD HH:mm:ss'"
        :value-format="item.props.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
      />`;
    case 'time':
      return `<el-time-picker
        v-model="formData.${component.field}"
        v-bind="item.props"
        :format="item.props.format || 'HH:mm:ss'"
        :value-format="item.props.valueFormat || 'HH:mm:ss'"
      />`;
    case 'image-upload':
      return `<el-upload
        v-model:file-list="formData.${component.field}"
        v-bind="${JSON.stringify(component.props)}"
      >
        <el-icon><Plus /></el-icon>
      </el-upload>`;
    case 'file-upload':
      return `<el-upload
        v-model:file-list="formData.${component.field}"
        v-bind="${JSON.stringify(component.props)}"
      >
        <el-button type="primary">点击上传</el-button>
      </el-upload>`;
    case 'rate':
      return `<el-rate
        v-model="formData.${component.field}"
        v-bind="${JSON.stringify(component.props)}"
      />`;
    case 'color-picker':
      return `<el-color-picker
        v-model="formData.${component.field}"
        v-bind="${JSON.stringify(component.props)}"
      />`;
    case 'slider':
      return `<el-slider
        v-model="formData.${component.field}"
        v-bind="${JSON.stringify(component.props)}"
      />`;
    case 'button':
      return `<el-button
        v-bind="${JSON.stringify(component.props)}"
        @click="handleButtonClick('${component.field}')"
      >${component.props.text}</el-button>`;
    case 'divider':
      return `<el-divider
        v-bind="${JSON.stringify(component.props)}"
      >${component.props.text}</el-divider>`;
    case 'dept-select':
      return `<el-tree-select
        v-model="formData.${component.field}"
        :data="deptOptions"
        :props="{ label: 'deptName', value: 'deptId' }"
        v-bind="${JSON.stringify(component.props)}"
        @change="handleDeptChange"
      />`;
    case 'user-select':
      return `<el-select
        v-model="formData.${component.field}"
        v-bind="${JSON.stringify(component.props)}"
        @change="handleUserChange"
      >
        <el-option
          v-for="user in userOptions"
          :key="user.userId"
          :label="user.nickName"
          :value="user.userId"
        />
      </el-select>`;
    case 'switch':
      return `<el-switch
        v-model="formData.${component.field}"
        v-bind="component.props"
        :active-text="component.props.activeText"
        :inactive-text="component.props.inactiveText"
      />`;
    case 'editor':
      return `<RichEditor
        v-model="formData.${component.field}"
        :placeholder="component.props.placeholder"
        :height="component.props.height"
        :min-height="component.props.minHeight"
        @change="handleComponentChange(component)"
      />`;
    default:
      return '';
  }
};

const vueTemplate = computed(() => {
  return `<template>
    <el-form :model="formData" :label-width="${deviceType.value === 'pc' ? '100px' : '80px'}">
      ${formComponents.value
        .map(
          (component) => `
      <el-form-item label="${component.label}">
        ${getComponentTemplate(component)}
      </el-form-item>`
        )
        .join('')}
    </el-form>
  </template>`;
});

const vueScript = computed(() => {
  return `<script lang="ts" setup>
import { reactive, ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listDept } from '@/api/system/dept'
import { listUser } from '@/api/system/user'
import { getDicts } from '@/api/system/dict/data'

const formData = reactive({
  ${formComponents.value
    .map((component) => {
      switch (component.type) {
        case 'checkbox':
          return `  ${component.field}: []`;
        case 'image-upload':
        case 'file-upload':
          return `  ${component.field}: []`;
        case 'rate':
          return `  ${component.field}: 0`;
        case 'color-picker':
          return `  ${component.field}: '#409EFF'`;
        case 'slider':
          return `  ${component.field}: 0`;
        case 'dept-select':
        case 'user-select':
          return `  ${component.field}: ${component.props.multiple ? '[]' : ''}`;
        default:
          return `  ${component.field}: ''`;
      }
    })
    .join(',\n')}
})

// 字典数据
const dictOptions = ref<Record<string, any[]>>({})

// 加载字典数据
const loadDictData = async (dictType: string) => {
  if (!dictType) return
  try {
    const res = await getDicts(dictType)
    if (res.code === 200) {
      const options = res.data.map((item: any) => ({
        label: item.dictLabel,
        value: item.dictValue
      }))
      dictOptions.value[dictType] = options
      
      // 更新使用该字典的组件的选项
      formComponents.value.forEach(component => {
        if (['select', 'radio', 'checkbox'].includes(component.type) && 
            component.props.dataSource === 'dict' && 
            component.props.dictType === dictType) {
          component.props.options = options
        }
      })
    }
  } catch (error) {
    console.error('获取字典数据失败:', error)
    ElMessage.error('获取字典数据失败')
  }
}

// 初始化时加载所有需要的字典数据
onMounted(async () => {
  // 加载所有使用字典的组件的字典数据
  const dictTypes = new Set<string>()
  formComponents.value.forEach(component => {
    if (['select', 'radio', 'checkbox'].includes(component.type) && 
        component.props.dataSource === 'dict' && 
        component.props.dictType) {
      dictTypes.add(component.props.dictType)
    }
  })
  
  // 并行加载所有字典数据
  await Promise.all(Array.from(dictTypes).map(dictType => loadDictData(dictType)))
})

// ... existing code ...
<\/script>`;
});

const vueStyle = computed(() => {
  return `<style lang="scss" scoped>
.form-container {
  padding: 20px;
}

.subtable-actions {
  margin-top: 10px;
  text-align: right;
}

:deep(.el-table) {
  .el-table__cell {
    padding: 0;
  }
  
  .el-table__body-wrapper {
    overflow: visible;
  }
}
</style>`;
});

const handleColumnChange = (columnName: string) => {
  const column = currentTableColumns.value.find((col) => col.columnName === columnName);
  if (!column) return;

  if (currentSubTableColumn.value) {
    // 处理子表列
    currentSubTableColumn.value.dbInfo.columnType = column.columnType;
    currentSubTableColumn.value.dbInfo.columnComment = column.columnComment;
    currentSubTableColumn.value.dbInfo.isNullable = column.isNullable;
    currentSubTableColumn.value.label = column.columnComment || column.columnName;
    updateSubTableColumnType(column);
  } else if (currentComponent.value) {
    // 处理普通组件
    currentComponent.value.dbInfo.columnType = column.columnType;
    currentComponent.value.dbInfo.columnComment = column.columnComment;
    currentComponent.value.dbInfo.isNullable = column.isNullable;
    currentComponent.value.label = column.columnComment || column.columnName;
    updateComponentType(column);
  }
};

const updateComponentType = (column: any) => {
  if (!currentComponent.value) return;

  let type = 'input';
  const props: any = {
    placeholder: `请输入${column.columnComment || column.columnName}`
  };

  if (column.columnType.includes('text') || (column.columnType.includes('varchar') && column.columnType.includes('(500)'))) {
    type = 'textarea';
  } else if (column.columnType.includes('int') || column.columnType.includes('decimal')) {
    type = 'input';
    props.type = 'number';
  } else if (column.columnType.includes('tinyint(1)')) {
    type = 'checkbox';
    props.label = column.columnComment || column.columnName;
  } else if (column.columnType.includes('datetime') || column.columnType.includes('date')) {
    type = 'input';
    props.type = 'date';
  }

  currentComponent.value.type = type;
  currentComponent.value.props = props;
};

const handleAddSubTableRow = (component: FormComponent) => {
  if (!masterSubTableData.value[component.field]) {
    masterSubTableData.value[component.field] = [];
  }

  const newRow: Record<string, any> = {};
  component.subTableColumns?.forEach((column) => {
    newRow[column.field] = '';
  });

  masterSubTableData.value[component.field].push(newRow);
};

const handleDeleteSubTableRow = (component: FormComponent, index: number) => {
  if (masterSubTableData.value[component.field]) {
    masterSubTableData.value[component.field].splice(index, 1);
  }
};

const handleSelectSubTable = async () => {
  if (!currentComponent.value) return;

  try {
    // 获取数据源列表
    const dataNames = await getDataNames();
    dataNameList.value = dataNames.data || [];

    // 设置默认数据源
    queryParams.value = {
      ...queryParams.value,
      tableName: 'master',
      pageNum: 1,
      pageSize: 10
    };

    // 获取表列表
    await getList();

    tableSelectorRef.value.visible = true;
    tableSelectorRef.value.isSubTable = true;
  } catch (error) {
    console.error('获取子表列表失败:', error);
    ElMessage.error('获取子表列表失败');
  }
};

const getColumnType = (columnType: string): string => {
  if (columnType.includes('text') || columnType.includes('varchar')) {
    return 'input';
  } else if (columnType.includes('int') || columnType.includes('decimal')) {
    return 'number';
  } else if (columnType.includes('tinyint(1)')) {
    return 'checkbox';
  } else if (columnType.includes('datetime') || columnType.includes('date')) {
    return 'date';
  }
  return 'input';
};

const handleTableSelect = (table: TableVO) => {
  if (!table || !table.columnsVo) return;

  // 主表处理 - 只处理选中的字段，但隐藏外键字段
  const mainComponents = table.columnsVo
    .filter((column: OnlFieldVo) => {
      // 过滤掉外键字段
      return !column.columnName.includes(table.subTableFkName || '');
    })
    .map((column: OnlFieldVo) => {
      let type = 'input';
      const props: any = {
        placeholder: `请输入${column.columnComment || column.columnName}`,
        clearable: true
      };

      // 根据字段类型设置对应的表单组件类型
      if (column.columnType === 'datetime' || column.columnType === 'date') {
        type = 'date-picker';
        props.type = column.columnType === 'datetime' ? 'datetime' : 'date';
      } else if (column.columnType === 'int' || column.columnType === 'bigint') {
        type = 'input-number';
        props.min = 0;
      } else if (column.columnType === 'varchar' && column.columnLength && parseInt(column.columnLength) > 200) {
        type = 'textarea';
      }

      return {
        type,
        label: column.columnComment || column.columnName,
        field: column.columnName,
        props,
        rules: column.isNullable === 'NO' ? [{ required: true, message: `请输入${column.columnComment || column.columnName}`, trigger: 'blur' }] : []
      };
    });

  return mainComponents;
};

// 获取表单值
const getFormValue = (field: string): any => {
  return formData.value[field];
};

// 设置表单值
const setFormValue = (field: string, value: any): void => {
  formData.value[field] = value;
};

// 获取子表数据
const getSubTableData = (tableName: string): any[] => {
  return masterSubTableData.value[tableName] || [];
};

// 处理组件属性
const handleComponentProps = (component: FormComponent, prop: string): any => {
  if (!component.props) return undefined;
  return component.props[prop];
};

// 添加选择子表列的方法
const selectSubTableColumn = (component: FormComponent, column: SubTableColumn) => {
  // 如果点击的是当前选中的列，则取消选中
  if (currentSubTableColumn.value?.id === column.id) {
    currentSubTableColumn.value = null;
    currentComponent.value = null;
  } else {
    currentSubTableColumn.value = column;
    currentComponent.value = component;
  }
};

const getComponentByType = (type: string) => {
  switch (type) {
    case 'input':
      return 'el-input';
    case 'textarea':
      return 'el-input';
    case 'select':
      return 'el-select';
    case 'checkbox':
      return 'el-checkbox';
    case 'date':
      return 'el-date-picker';
    case 'datetime':
      return 'el-date-picker';
    case 'time':
      return 'el-time-picker';
    case 'image-upload':
      return 'el-upload';
    case 'file-upload':
      return 'el-upload';
    case 'rate':
      return 'el-rate';
    case 'color-picker':
      return 'el-color-picker';
    case 'slider':
      return 'el-slider';
    case 'button':
      return 'el-button';
    case 'divider':
      return 'el-divider';
    case 'dept-select':
      return 'el-tree-select';
    case 'user-select':
      return 'el-select';
    case 'popup':
      return 'popup-table';
    default:
      return 'el-input';
  }
};

const handleDeleteSubTableColumn = (component: FormComponent, column: SubTableColumn) => {
  if (!component.subTableColumns) return;

  const index = component.subTableColumns.findIndex((col) => col.id === column.id);
  if (index !== -1) {
    component.subTableColumns.splice(index, 1);
    currentSubTableColumn.value = null;
  }
};

const handleCopySubTableColumn = (component: FormComponent, column: SubTableColumn) => {
  if (!component.subTableColumns) return;

  const newColumn: SubTableColumn = {
    ...column,
    id: `column_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    field: `field_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  };

  component.subTableColumns.push(newColumn);
  currentSubTableColumn.value = newColumn;
};

const updateSubTableColumnType = (column: any) => {
  if (!currentSubTableColumn.value) return;

  let type = 'input';
  const props: any = {
    placeholder: `请输入${column.columnComment || column.columnName}`
  };

  if (column.columnType.includes('text') || (column.columnType.includes('varchar') && column.columnType.includes('(500)'))) {
    type = 'textarea';
  } else if (column.columnType.includes('int') || column.columnType.includes('decimal')) {
    type = 'input';
    props.type = 'number';
  } else if (column.columnType.includes('tinyint(1)')) {
    type = 'checkbox';
    props.label = column.columnComment || column.columnName;
  } else if (column.columnType.includes('datetime') || column.columnType.includes('date')) {
    type = 'input';
    props.type = 'date';
  }

  currentSubTableColumn.value.type = type;
  currentSubTableColumn.value.props = props;
};

// 定义表单验证规则
const rules = {
  formName: [{ required: true, message: '请输入表单名称', trigger: 'blur' }]
};

// 修改保存表单方法
const handleSave = () => {
  dialog.visible = true;
  dialog.title = '保存表单';

  // 如果是编辑模式，从表单配置中获取表单名称和描述
  if (props.formData && props.formData.formContent) {
    try {
      const formJson = JSON.parse(props.formData.formContent) as FormContent;
      if (formJson.formConfig) {
        form.formName = formJson.formConfig.formName || form.formName;
        form.formDesc = formJson.formConfig.formDesc || form.formDesc;
      }
    } catch (error) {
      console.error('解析表单内容失败:', error);
    }
  }
};

// 修改提交表单方法
const submitForm = async () => {
  try {
    // 验证表单数据
    if (!form.formName || !form.formDesc) {
      ElMessage.error('表单名称和描述不能为空');
      return;
    }

    // 构建表单JSON
    const tableInfo = {
      mainTable: {
        tableName: '',
        tableComment: '',
        columns: []
      }
    };

    // 从表单组件中提取表信息
    if (formComponents.value.length > 0) {
      // 从第一个组件的 dbInfo 中获取表信息
      const firstComponent = formComponents.value[0];
      if (firstComponent.dbInfo?.tableName) {
        tableInfo.mainTable.tableName = firstComponent.dbInfo.tableName;
        tableInfo.mainTable.tableComment = firstComponent.dbInfo.tableComment || '';

        // 从所有组件的 dbInfo 中提取列信息
        const columns = formComponents.value
          .filter((comp) => comp.dbInfo?.columnName)
          .map((comp) => ({
            columnName: comp.dbInfo.columnName,
            columnType: comp.dbInfo.columnType,
            columnComment: comp.dbInfo.columnComment,
            isNullable: comp.dbInfo.isNullable,
            isRequired: comp.required ? '1' : '0',
            dictType: comp.dictType,
            htmlType: comp.type,
            dictFields: comp.dictFields,
            mapping: comp.mapping
          }));

        tableInfo.mainTable.columns = columns;
      }
    }

    // 构建主子表数据
    let masterSubTableData = {};

    // 查找子表组件
    const subTableComponents = formComponents.value.filter((comp) => comp.type === 'subtable');
    if (subTableComponents.length > 0) {
      const subTableComponent = subTableComponents[0];
      masterSubTableData.subTableName = subTableComponent.subTableName;
      masterSubTableData.subTableFkName = subTableComponent.subTableFkName;
      masterSubTableData.subColumns = subTableComponent.subTableColumns || [];
    } else {
      // 如果没有子表组件，尝试从 props.formData 中获取主子表数据
      if (props.formData?.formContent) {
        try {
          const existingContent = JSON.parse(props.formData.formContent);
          if (existingContent.masterSubTableData) {
            masterSubTableData = existingContent.masterSubTableData;
          }
        } catch (error) {
          console.error('解析现有表单内容失败:', error);
        }
      }
    }

    const formJson = {
      formConfig: {
        labelWidth: deviceType.value === 'pc' ? '100px' : '80px',
        labelPosition: 'right',
        size: 'default',
        formName: form.formName,
        formDesc: form.formDesc,
        tableInfo: tableInfo
      },
      formComponents: formComponents.value.map((component) => ({
        ...component,
        dbInfo: {
          ...component.dbInfo,
          tableInfo: tableInfo
        }
      })),
      masterSubTableData: masterSubTableData
    };

    // 设置表单内容
    form.formContent = JSON.stringify(formJson);

    // 如果是编辑模式，保留原有的formId
    if (props.formData?.formId) {
      form.formId = props.formData.formId;
    }

    // 触发保存事件
    emit('save', form);
    dialog.visible = false;
  } catch (error) {
    console.error('保存表单失败:', error);
    ElMessage.error('保存表单失败: ' + (error as Error).message);
  }
};

// 修改关闭方法
const handleClose = () => {
  if (hasChanges()) {
    ElMessageBox.confirm('确定要关闭表单设计器吗？未保存的更改将会丢失。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
      .then(() => {
        emit('close');
      })
      .catch(() => {
        // 用户点击取消，不做任何操作
      });
  } else {
    // 没有修改，直接关闭
    emit('close');
  }
};

// 添加修改检测方法
const hasChanges = () => {
  if (!originalFormData.value) {
    // 新建表单，检查是否有内容
    const hasContent = form.formName || form.formDesc || formComponents.value.length > 0;
    return hasContent;
  }

  // 编辑表单，比较基本信息
  const nameChanged = form.formName !== originalFormData.value.formName;
  const descChanged = form.formDesc !== originalFormData.value.formDesc;

  // 比较表单组件是否改变 - 使用更简单的比较方式
  let componentsChanged = false;
  try {
    const originalContent = JSON.parse(originalFormData.value.formContent);
    const originalComponents = originalContent.formComponents || [];
    componentsChanged = JSON.stringify(formComponents.value) !== JSON.stringify(originalComponents);
  } catch (error) {
    console.error('解析原始表单内容失败:', error);
    componentsChanged = true; // 如果解析失败，认为有改变
  }

  const hasChanges = nameChanged || descChanged || componentsChanged;

  return hasChanges;
};

// 监听表单数据变化
watch(
  () => props.formData,
  () => {
    loadFormData();
  },
  { immediate: true }
);

// 添加onMounted钩子
onMounted(() => {
  loadFormData();
  getDataNameList();
  getList();
  loadDeptOptions();
  loadUserOptions();
  loadDictTypeOptions(); // 添加加载字典类型列表
});

// 添加布局组件的拖拽处理方法
const handleLayoutDrop = (e: DragEvent, target: any) => {
  const data = e.dataTransfer?.getData('text/plain');
  if (!data) return;

  const component = JSON.parse(data);
  const newComponent = {
    ...component,
    id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    field: `field_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    required: false,
    dbInfo: {
      tableName: '',
      columnName: '',
      columnType: '',
      columnComment: '',
      isNullable: 'YES'
    }
  };

  if (!target.children) {
    target.children = [];
  }

  target.children.push(newComponent);
  currentComponent.value = newComponent;
  formData.value[newComponent.field] = '';
};

// 添加按钮点击处理方法
const handleButtonClick = (field: string) => {
  // 这里可以添加按钮点击后的处理逻辑
};

// 监听组件变化，重新初始化表单数据
watch(
  formComponents,
  () => {
    initFormData();
  },
  { deep: true }
);

// 在 script 部分添加新的响应式变量
const activePropertiesTab = ref('field');
const labelPosition = ref('right');
const formSize = ref('default');

// 在 script 部分添加数据和方法
const deptOptions = ref([]);
const userOptions = ref([]);

// 加载部门数据
const loadDeptOptions = async () => {
  try {
    const res = await listDept();
    if (res.code === 200) {
      deptOptions.value = res.data;
    } else {
      ElMessage.error(res.msg || '获取部门列表失败');
    }
  } catch (error) {
    console.error('获取部门列表失败:', error);
    ElMessage.error('获取部门列表失败');
  }
};

// 加载用户数据
const loadUserOptions = async () => {
  try {
    const res = await listUser();
    if (res.code === 200) {
      userOptions.value = res.rows;
    } else {
      ElMessage.error(res.msg || '获取用户列表失败');
    }
  } catch (error) {
    console.error('获取用户列表失败:', error);
    ElMessage.error('获取用户列表失败');
  }
};

// 部门选择变化处理
const handleDeptChange = (value: any) => {
  // 这里可以添加部门选择后的业务逻辑
};

// 用户选择变化处理
const handleUserChange = (value: any) => {
  // 这里可以添加用户选择后的业务逻辑
};

// 在 script 部分添加预览相关的方法
const handlePreviewSubmit = () => {
  if (!previewFormRef.value) return;

  previewFormRef.value.validate((valid: boolean) => {
    if (valid) {
      ElMessage.success('提交成功');
      previewVisible.value = false;
    } else {
      ElMessage.error('请完善表单信息');
      return false;
    }
  });
};

// 添加表单验证规则
const formRules = ref({
  // 这里可以根据需要添加表单验证规则
});

// 添加预览表单引用
const previewFormRef = ref();

// 在 script 部分修改预览相关的方法
const previewDeptOptions = ref([]);
const previewUserOptions = ref([]);

// 预览对话框打开时加载数据
const handlePreviewOpen = async () => {
  try {
    // 加载部门数据
    const deptRes = await listDept();
    if (deptRes.code === 200) {
      previewDeptOptions.value = deptRes.data;
    } else {
      ElMessage.error(deptRes.msg || '获取部门列表失败');
    }

    // 加载用户数据
    const userRes = await listUser();
    if (userRes.code === 200) {
      previewUserOptions.value = userRes.rows;
    } else {
      ElMessage.error(userRes.msg || '获取用户列表失败');
    }

    // 加载所有组件的字典数据
    for (const component of formComponents.value) {
      if (['select', 'radio', 'checkbox'].includes(component.type) && component.props.dataSource === 'dict' && component.props.dictType) {
        await loadDictData(component.props.dictType);
      }
    }

    // 初始化表单数据
    initPreviewFormData();
  } catch (error) {
    console.error('加载预览数据失败:', error);
    ElMessage.error('加载预览数据失败');
  }
};

// 初始化预览表单数据
const initPreviewFormData = () => {
  formComponents.value.forEach((component: FormComponent) => {
    if (component.type === 'subtable') {
      if (!masterSubTableData.value[component.field]) {
        masterSubTableData.value[component.field] = [];
      }
    } else if (!formData.value[component.field]) {
      switch (component.type) {
        case 'dept-select':
        case 'user-select':
          formData.value[component.field] = component.props.multiple ? [] : '';
          break;
        case 'image-upload':
        case 'file-upload':
          formData.value[component.field] = [];
          break;
        case 'rate':
        case 'slider':
          formData.value[component.field] = 0;
          break;
        case 'color-picker':
          formData.value[component.field] = '#409EFF';
          break;
        case 'checkbox':
          formData.value[component.field] = [];
          break;
        default:
          formData.value[component.field] = '';
      }
    }
  });
};

// 预览时的部门选择变化处理
const handlePreviewDeptChange = (value: any, field: string) => {
  formData.value[field] = value;
};

// 预览时的用户选择变化处理
const handlePreviewUserChange = (value: any, field: string) => {
  formData.value[field] = value;
};

const handleFieldChange = (value: any, item: FormComponent) => {
  formData.value[item.field] = value;
};

// 在 script 部分添加 updateComponent 方法
const updateComponent = async (key?: string, value?: any) => {
  if (!currentComponent.value) return;
  // 监听 dictType 变化
  if (key === 'dictType' && value) {
    const options = await fetchDictOptions(value);
    if (['select', 'radio', 'checkbox'].includes(currentComponent.value.type)) {
      currentComponent.value.props.options = options;
    }
  }
};

// 添加数据库配置引用
const databaseConfigRef = ref();

// 添加设计器可见性状态
const designerVisible = ref(false);

// 字典数据缓存
const dictCache = ref<Record<string, any[]>>({});

// 拉取字典数据
const fetchDictOptions = async (dictType: string): Promise<any[]> => {
  if (!dictType) return [];
  if (dictCache.value[dictType]) return dictCache.value[dictType];
  try {
    const res: any = await (optionselect as any)(dictType);
    if (res.code === 200) {
      const options = res.data.map((item: any) => ({
        label: item.dictLabel,
        value: item.dictValue
      }));
      dictCache.value[dictType] = options;
      return options;
    }
  } catch (e) {
    console.error('字典拉取失败', e);
  }
  return [];
};

// 获取字典数据
async function loadDictData(dictType: string) {
  if (!dictType) return;
  try {
    const res = await getDicts(dictType);
    if (res.code === 200) {
      // 将字典数据转换为选项格式
      const options = (res.data as DictDataVO[]).map((item) => ({
        label: item.dictLabel,
        value: item.dictValue
      }));
      // 更新字典缓存
      dictOptions.value[dictType] = options;
    } else {
      ElMessage.error('获取字典数据失败');
    }
  } catch (error) {
    console.error('获取字典数据出错:', error);
    ElMessage.error('获取字典数据出错');
  }
}

// 监听字典类型变化
watch(
  () => currentComponent.value?.props.dictType,
  (newVal) => {
    if (newVal) {
      loadDictData(newVal);
    }
  },
  { immediate: true }
);

// 字典类型选项
const dictTypeOptions = ref<DictTypeVO[]>([]);

// 获取字典类型列表
const loadDictTypeOptions = async () => {
  try {
    const res = await optionselect();
    if (res.code === 200) {
      dictTypeOptions.value = res.data;
    } else {
      ElMessage.error(res.msg || '获取字典类型列表失败');
    }
  } catch (error) {
    console.error('获取字典类型列表失败:', error);
    ElMessage.error('获取字典类型列表失败');
  }
};

// 处理字典类型变更
const handleDictTypeChange = async (dictType: string) => {
  if (!dictType || !currentComponent.value) return;

  try {
    const options = await getDictOptions(dictType);
    if (options.length > 0) {
      currentComponent.value.props.options = options;
      currentComponent.value.props.dataSource = 'dict';
      currentComponent.value.dictType = dictType; // 更新组件的 dictType
      updateComponent();
    }
  } catch (error) {
    console.error('获取字典数据失败:', error);
    ElMessage.error('获取字典数据失败');
  }
};

// 监听数据来源变化
watch(
  () => currentComponent.value?.props.dataSource,
  (newVal) => {
    if (!currentComponent.value) return;

    if (newVal === 'static') {
      // 切换到静态数据时，如果没有选项则添加默认选项
      if (!currentComponent.value.props.options || currentComponent.value.props.options.length === 0) {
        currentComponent.value.props.options = [
          { label: '选项1', value: '1' },
          { label: '选项2', value: '2' }
        ];
      }
      currentComponent.value.props.dictType = '';
    } else if (newVal === 'dict') {
      // 切换到数据字典时，清空选项
      currentComponent.value.props.options = [];
    }
    updateComponent();
  },
  { immediate: true }
);

// 监听 currentComponent 的变化
watch(
  () => currentComponent.value,
  (newVal) => {
    if (newVal && ['select', 'radio', 'checkbox'].includes(newVal.type)) {
      // 如果组件有 dictType 值，则自动设置为数据字典模式
      if (newVal.dictType) {
        newVal.props.dataSource = 'dict';
        newVal.props.dictType = newVal.dictType;
        // 加载字典数据
        loadDictData(newVal.dictType);
      }
    }
  },
  { immediate: true }
);

const handleComponentChange = (value: any, component: FormComponent) => {
  // 更新组件值
  if (component) {
    component.value = value;
  }

  // 处理联动规则
  if (component?.props?.onChange) {
    try {
      const rules = component.props.onChange;
      if (rules.onChange) {
        const { field, value: targetValue } = rules.onChange;
        if (typeof field === 'string') {
          // 找到目标组件并更新其值
          const targetComponent = formContent.value.formComponents.find((comp) => comp.field === field);
          if (targetComponent) {
            targetComponent.value = targetValue;
          }
        }
      }
    } catch (error) {
      console.error('处理联动规则失败:', error);
    }
  }

  // 处理字段映射
  if (component?.props?.mapping) {
    try {
      const mapping = component.props.mapping;
      if (mapping[component.field]) {
        const targetField = mapping[component.field];
        // 找到目标组件并更新其值
        const targetComponent = formContent.value.formComponents.find((comp) => comp.field === targetField);
        if (targetComponent) {
          targetComponent.value = value;
        }
      }
    } catch (error) {
      console.error('处理字段映射失败:', error);
    }
  }
};

const getFilteredProps = (props: any) => {
  const filteredProps = { ...props };
  delete filteredProps.dataSource;
  delete filteredProps.dictType;
  delete filteredProps.dictFields;
  delete filteredProps.mapping;
  delete filteredProps.onCodeGenerate;
  return filteredProps;
};

const showDebug = ref(false);
const debugInfo = computed(() => ({
  rootCount: formComponents.value?.length || 0,
  rootIds: (formComponents.value || []).map((c) => c?.id),
  selectedId: currentComponent.value?.id || null
}));

// 全局点击监听，辅助确认是否点击到了删除按钮
onMounted(() => {
  const handler = (e: Event) => {
    const t = e.target as HTMLElement;
    const btn = t.closest('[data-action="delete-root-component"]') as HTMLElement | null;
    if (btn) {
    }
  };
  document.addEventListener('click', handler, true);
  // 命中删除按钮时，直接触发删除，作为兜底
  document.addEventListener(
    'click',
    (e: Event) => {
      const t = e.target as HTMLElement;
      const btn = t.closest('[data-action="delete-root-component"]') as HTMLElement | null;
      if (btn) {
        handleDeleteComponent();
      }
    },
    true
  );
});

const onDeleteClick = (e: Event) => {
  e?.preventDefault?.();
  e?.stopPropagation?.();
  handleDeleteComponent();
};
</script>

<style lang="scss" scoped>
.designer-container {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
  padding: 0;
  margin: 0;
  overflow: hidden;

  .designer-header {
    height: 50px;
    padding: 0 20px;
    background-color: #fff;
    border-bottom: 1px solid #dcdfe6;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;

    h2 {
      margin: 0;
      font-size: 16px;
      font-weight: 500;
    }

    .el-button {
      padding: 0;
      font-size: 20px;
    }
  }

  .designer-content {
    flex: 1;
    display: flex;
    height: calc(100% - 50px);
    overflow: hidden;
    padding: 20px;
    box-sizing: border-box;

    .components-panel,
    .canvas-panel,
    .properties-panel {
      height: 100%;
      background-color: #fff;
      border-radius: 4px;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .components-panel {
      width: 250px;
      margin-right: 10px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      height: 100%;

      .panel-header {
        flex-shrink: 0;
      }

      .components-list {
        flex: 1;
        overflow: hidden;
        padding: 10px;

        .el-scrollbar {
          height: 100%;
        }

        .component-group {
          margin-bottom: 20px;

          .group-title {
            font-size: 14px;
            color: #909399;
            margin-bottom: 10px;
            padding-left: 10px;
            border-left: 3px solid #409eff;
          }

          .component-items {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 8px;
            padding: 0 10px;

            .component-item {
              display: flex;
              align-items: center;
              padding: 8px;
              border: 1px solid #dcdfe6;
              border-radius: 4px;
              cursor: pointer;
              transition: all 0.3s;
              background-color: #fff;

              &:hover {
                border-color: #409eff;
                color: #409eff;
              }

              .el-icon {
                margin-right: 8px;
              }

              span {
                font-size: 12px;
              }
            }
          }
        }
      }
    }

    .canvas-panel {
      flex: 1;
      margin: 0 10px;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }

    .properties-panel {
      width: 300px;
      margin-left: 10px;
      flex-shrink: 0;
    }

    .panel-header {
      height: 40px;
      padding: 0 15px;
      border-bottom: 1px solid #dcdfe6;
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-shrink: 0;

      h3 {
        margin: 0;
        font-size: 14px;
        font-weight: 500;
      }
    }

    .canvas-content,
    .properties-content {
      flex: 1;
      overflow: hidden;
      position: relative;
    }

    .canvas-content {
      .form-canvas {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        overflow-y: auto;
        padding: 20px;
        background-color: #fff;
        border: 1px dashed #dcdfe6;
        margin: 20px;
      }
    }

    .properties-content {
      padding: 15px;
      overflow-y: auto;
    }
  }
}

.form-item {
  padding: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  position: relative;

  &:hover {
    border-color: #409eff;
  }

  &.selected {
    border-color: #409eff;
    background-color: #f0f9ff;
  }

  .form-item-actions {
    position: absolute;
    top: 10px;
    right: 10px;
    display: flex;
    gap: 5px;
    opacity: 0;
    transition: opacity 0.3s;

    .el-button {
      padding: 4px 8px;
    }
  }

  &:hover .form-item-actions,
  &.selected .form-item-actions {
    opacity: 1;
  }
}

.mobile-view {
  max-width: 375px;
  margin: 0 auto;
  padding: 20px;
}

.mobile-dialog {
  .el-dialog {
    max-width: 375px;
    margin: 0 auto;
  }
}

.dialog-scroll-content {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 10px;
}

.code-block {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  font-family: monospace;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 50vh;
  overflow-y: auto;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.subtable-container {
  position: relative;
  border: 1px dashed #dcdfe6;
  padding: 10px;
  border-radius: 4px;

  .subtable-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    padding: 0 10px;
  }

  .subtable-content {
    margin-bottom: 10px;
  }

  .subtable-drop-area {
    min-height: 100px;
    margin-top: 10px;
    border: 1px dashed #409eff;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      border-color: #409eff;
      background-color: #f0f9ff;
    }
  }
}

.selected-column {
  background-color: #f0f9ff;
  border: 1px solid #409eff;
}

.column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  padding: 8px;
  border-radius: 4px;
  transition: all 0.3s;

  &:hover {
    background-color: #f5f7fa;
  }

  &.selected-column {
    background-color: #f0f9ff;
    border: 1px solid #409eff;
  }

  .column-title {
    flex: 1;
  }

  .column-actions {
    display: flex;
    gap: 5px;
    margin-left: 10px;
  }
}

.layout-drop-area {
  min-height: 100px;
  padding: 10px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  margin: 5px;

  &:hover {
    border-color: #409eff;
    background-color: #f0f9ff;
  }
}

.table-drop-area {
  min-height: 100px;
  padding: 10px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  margin: 5px;
  background-color: #fff;

  &:hover {
    border-color: #409eff;
    background-color: #f0f9ff;
  }

  :deep(.el-empty) {
    padding: 20px 0;
  }
}

:deep(.el-table) {
  .el-table__cell {
    padding: 0;
  }

  .el-table__body-wrapper {
    overflow: visible;
  }
}

.properties-form {
  padding: 10px;

  .component-type-tags {
    display: flex;
    gap: 8px;
    align-items: center;

    .type-tag {
      min-width: 60px;
      text-align: center;
    }

    .dict-tag {
      min-width: 80px;
      text-align: center;
    }
  }

  .full-width {
    width: 100%;
  }

  :deep(.el-form-item) {
    margin-bottom: 18px;

    .el-form-item__label {
      font-size: 13px;
      font-weight: normal;
      color: #606266;
      line-height: 32px;
    }

    .el-input,
    .el-select {
      width: 100%;

      .el-input__wrapper {
        padding: 0 11px;
      }

      .el-input__inner {
        height: 32px;
        line-height: 32px;
        font-size: 13px;
      }
    }

    .el-switch {
      height: 32px;
    }
  }

  :deep(.el-divider) {
    margin: 20px 0;

    .el-divider__text {
      font-size: 13px;
      font-weight: normal;
      color: #606266;
      background-color: #f5f7fa;
    }
  }
}

.debug-info {
  background: #1e1e1e;
  color: #c7f0ff;
  padding: 8px;
  margin-top: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 180px;
  overflow: auto;
}
</style>
