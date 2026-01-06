<template>
	<view class="container">
		<view class="top-section">
			<view class="back-btn" @tap="goBack">
				<u-icon name="arrow-left" size="20"></u-icon>
			</view>
			<view class="title">短信验证</view>
		</view>
		
		<view class="content">
			<view class="desc">验证码已发送至 {{ phoneNumber }}</view>
			<view class="resend-container">
				<u-verification-code :seconds="60" @start="startTimer" :start-text="'重新获取'" :change-text="'重新获取'"></u-verification-code>
			</view>
			
			<view class="code-inputs">
				<u-code-input 
					v-model="code" 
					:maxlength="6"
					:space="10"
					:size="50"
					:disabled-keyboard="false"
					@finish="onFinish"
				></u-code-input>
			</view>
			
			<view class="tips">收不到验证码？使用密码登录</view>
			
			<button class="login-btn" :disabled="!code || code.length < 6" @tap="submitCode">
				登录
			</button>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getCurrentInstance } from "vue";
import { useUserStore } from '@/store';

const { proxy } = getCurrentInstance();
const code = ref('');
const phoneNumber = ref('');

// 从上一页传递手机号参数
onLoad((options) => {
	if(options.phone) {
		phoneNumber.value = options.phone;
	}
});

// 验证码输入完成回调
const onFinish = (val) => {
	console.log('验证码输入完成：', val);
};

// 开始倒计时
const startTimer = () => {
	// 这里可以触发重新发送验证码的API调用
};

// 提交验证码
const submitCode = async () => {
	if(code.value.length !== 6) {
		uni.$modal.msgError('请输入完整的验证码');
		return;
	}
	
	proxy.$modal.loading("登录中，请耐心等待...");
	
	try {
		// 调用短信登录接口
		await useUserStore().smsLogin(phoneNumber.value, code.value);
		proxy.$modal.closeLoading();
		
		// 获取用户信息并跳转
		await useUserStore().getInfo();
		proxy.$tab.reLaunch('/pages/index');
	} catch (error) {
		proxy.$modal.closeLoading();
		uni.$modal.msgError(error.message || '登录失败');
	}
};

// 返回上一页
const goBack = () => {
	proxy.$tab.navigateBack();
};
</script>

<style lang="scss" scoped>
.container {
	height: 100vh;
	background: #fff;
	
	.top-section {
		display: flex;
		align-items: center;
		padding: 40rpx 30rpx 60rpx;
		
		.back-btn {
			width: 60rpx;
			height: 60rpx;
			display: flex;
			align-items: center;
			justify-content: center;
			border-radius: 50%;
			background: #f5f5f5;
		}
		
		.title {
			flex: 1;
			text-align: center;
			font-size: 36rpx;
			font-weight: 500;
			margin-left: -30rpx;
		}
	}
	
	.content {
		padding: 0 60rpx;
		
		.desc {
			font-size: 28rpx;
			color: $uni-text-color;
			text-align: center;
			margin-bottom: 40rpx;
		}
		
		.resend-container {
			display: flex;
			justify-content: flex-end;
			margin-bottom: 60rpx;
		}
		
		.code-inputs {
			margin: 40rpx 0;
			display: flex;
			justify-content: center;
		}
		
		.tips {
			text-align: center;
			font-size: 24rpx;
			color: $uni-text-color-grey;
			margin: 40rpx 0;
		}
		
		.login-btn {
			background: #f9c00c;
			color: #fff;
			border: none;
			height: 80rpx;
			line-height: 80rpx;
			border-radius: 40rpx;
			font-size: 30rpx;
			margin-top: 60rpx;
			
			&[disabled] {
				background: #f5f5f5;
				color: $uni-text-color-disable;
			}
		}
	}
}
</style>