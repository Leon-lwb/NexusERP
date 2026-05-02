<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" label-width="120px">
      <el-form-item label="订单内容" prop="orderContent">
        <el-input v-model="form.orderContent" placeholder="请输入订单内容" />
      </el-form-item>
      <el-form-item label="订单金额(分)" prop="totalAmount">
        <el-input v-model="form.totalAmount" placeholder="请输入订单金额(分)，如 1" />
      </el-form-item>
      <el-form-item label="支付方式" prop="payType">
        <el-select v-model="form.payType" placeholder="请选择支付方式" style="width: 200px">
          <el-option v-for="dict in pay_pay_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleCreateOrderAndPay">创建订单并获取支付链接</el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card v-if="orderNumber" class="result-card">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>支付信息</span>
          <el-tag v-if="polling" type="warning" effect="dark">
            <el-icon class="is-loading"><i class="el-icon-loading" /></el-icon>
            等待支付中...
          </el-tag>
          <el-tag v-else-if="orderStatus === '已支付'" type="success" effect="dark">支付成功</el-tag>
        </div>
      </template>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="订单号">{{ orderNumber }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <dict-tag :options="pay_order_status" :value="orderStatus" />
        </el-descriptions-item>
      </el-descriptions>

      <!-- 支付成功 -->
      <div v-if="orderStatus === '已支付'" style="margin-top: 16px">
        <el-result icon="success" title="支付成功" sub-title="订单已完成支付">
          <template #extra>
            <el-button type="primary" @click="resetForm">创建新订单</el-button>
          </template>
        </el-result>
      </div>

      <!-- 二维码支付中 -->
      <div v-else-if="qrCodeUrl" style="margin-top: 16px">
        <el-alert title="请使用沙箱版支付宝APP扫描二维码" type="warning" :closable="false" show-icon style="margin-bottom: 12px" />
        <div style="text-align: center; margin: 16px 0">
          <img :src="'https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=' + encodeURIComponent(qrCodeUrl)" style="border: 1px solid #eee; padding: 8px; max-width: 100%" />
        </div>
        <div style="display: flex; gap: 8px; justify-content: center; flex-wrap: wrap">
          <el-button @click="queryOrderStatus" :loading="querying">手动查询状态</el-button>
          <el-button v-if="!polling" type="primary" @click="startPolling">开始轮询等待支付</el-button>
          <el-button v-else type="danger" @click="stopPolling">停止轮询</el-button>
        </div>
      </div>

      <!-- 网页支付模式 -->
      <div v-else-if="payFormHtml" style="margin-top: 16px">
        <el-alert title="点击下方按钮，在新窗口完成支付" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
        <div style="text-align: center; padding: 30px 0">
          <el-button type="primary" size="large" @click="openPayUrl">在浏览器中打开支付宝支付页面</el-button>
        </div>
        <div style="text-align: center; margin-top: 12px">
          <el-button @click="queryOrderStatus" :loading="querying">查询支付状态</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="PayTest">
import { addPayOrder } from "@/api/pay/order"
import request from "@/utils/request"

const { proxy } = getCurrentInstance()
const { pay_pay_type, pay_order_status } = proxy.useDict('pay_pay_type', 'pay_order_status')

const formRef = ref(null)
const orderNumber = ref("")
const orderStatus = ref("")
const qrCodeUrl = ref("")
const payFormHtml = ref("")
const querying = ref(false)
const polling = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  orderContent: "测试商品",
  totalAmount: "1",
  payType: "alipay",
})

function resetForm() {
  stopPolling()
  form.orderContent = "测试商品"
  form.totalAmount = "1"
  form.payType = "alipay"
  orderNumber.value = ""
  orderStatus.value = ""
  qrCodeUrl.value = ""
  payFormHtml.value = ""
}

async function handleCreateOrderAndPay() {
  try {
    const res = await addPayOrder({
      orderContent: form.orderContent,
      totalAmount: form.totalAmount,
      payType: form.payType,
    })
    const order = res.data
    orderNumber.value = order.orderNumber

    const payRes = await request({
      url: `/pay/${form.payType}/url/${order.orderNumber}`,
      method: 'get'
    })
    const data = payRes.data

    if (data && (data.startsWith('https://') || data.startsWith('alipay:'))) {
      qrCodeUrl.value = data
      payFormHtml.value = ""
    } else if (data && data.startsWith('<form')) {
      payFormHtml.value = data
      qrCodeUrl.value = ""
    } else {
      proxy.$modal.msgError("未知的支付链接格式")
      return
    }
    orderStatus.value = "waiting"
    proxy.$modal.msgSuccess("订单创建成功，请使用沙箱版支付宝扫码")
    // 自动开始轮询
    startPolling()
  } catch (error) {
    proxy.$modal.msgError("操作失败：" + (error.message || "未知错误"))
  }
}

function openPayUrl() {
  if (!payFormHtml.value) return
  const win = window.open('', '_blank')
  if (win) {
    win.document.write(payFormHtml.value)
    win.document.close()
  }
}

async function queryOrderStatus() {
  if (!orderNumber.value) return
  try {
    querying.value = true
    const res = await request({
      url: `/pay/query/${orderNumber.value}`,
      method: 'post'
    })
    orderStatus.value = res.data.orderStatus
    if (res.data.orderStatus === '已支付') {
      stopPolling()
      proxy.$modal.msgSuccess("支付成功！")
    } else {
      proxy.$modal.msgInfo("当前状态：" + (res.data.orderStatus || "未知"))
    }
  } catch (error) {
    proxy.$modal.msgError("查询失败: " + (error.message || ""))
  } finally {
    querying.value = false
  }
}

function startPolling() {
  if (polling.value) return
  polling.value = true
  // 立即查询一次
  queryOrderStatusSilent()
  // 每5秒轮询一次，最多轮询30次（2.5分钟）
  let count = 0
  pollTimer = setInterval(() => {
    count++
    if (count > 30 || orderStatus.value === '已支付') {
      stopPolling()
      return
    }
    queryOrderStatusSilent()
  }, 5000)
}

function stopPolling() {
  polling.value = false
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function queryOrderStatusSilent() {
  if (!orderNumber.value) return
  try {
    const res = await request({
      url: `/pay/query/${orderNumber.value}`,
      method: 'post'
    })
    const newStatus = res.data.orderStatus
    if (newStatus !== orderStatus.value) {
      orderStatus.value = newStatus
      if (newStatus === '已支付') {
        stopPolling()
        proxy.$modal.msgSuccess("支付成功！检测到支付已完成")
      }
    }
  } catch {
    // 静默轮询，忽略错误
  }
}
</script>

<style scoped>
.result-card {
  margin-top: 20px;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.is-loading {
  animation: rotating 1s linear infinite;
}
</style>
