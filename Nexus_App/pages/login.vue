<template>
	<view class="wrap">
		<view class="top"></view>
		<view class="content">
			<view class="title">欢迎登录</view>
			<!-- 短信验证码登录 -->
			<view v-if="!showPasswordLogin">
				<input class="u-border-bottom" type="number" v-model="tel" placeholder="请输入手机号" maxlength="11" />
				<view class="tips">未注册的手机号验证后自动创建账号</view>
				
				<!-- 验证码输入区域，获取验证码后显示 -->
				<view v-if="showCodeInput" class="code-section">
					<view class="code-tips">验证码已发送至 {{ tel }}</view>
					<input
						class="u-border-bottom code-input"
						type="number"
						v-model="code"
						placeholder="请输入6位验证码"
						maxlength="6"
					/>
					<button
						@tap="submitCode"
						:style="[codeButtonStyle]"
						class="verify-btn"
						:disabled="!code || code.length !== 6"
					>
						验证并登录
					</button>
					<view class="resend-tips">
						没有收到验证码？
						<text class="resend-link" @tap="getSmsCode" v-if="countDown <= 0">重新发送</text>
						<text class="resend-countdown" v-else>{{ countDown }}秒后重试</text>
					</view>
				</view>
				
				<!-- 获取验证码按钮，未获取验证码时显示 -->
				<button
					v-else
					@tap="getSmsCode"
					:style="[inputStyle]"
					class="getCaptcha"
				>
					{{ countDown > 0 ? `${countDown}秒后重发` : '获取短信验证码' }}
				</button>
			</view>
			<!-- 密码登录 -->
			<view v-else>
				<input class="u-border-bottom" type="text" v-model="username" placeholder="请输入账号或手机号" maxlength="30" />
				<input class="u-border-bottom" type="password" v-model="password" placeholder="请输入密码" maxlength="20" />
			</view>
			<!-- 只在密码登录模式下显示登录按钮 -->
			<button
				@tap="submit"
				:style="[loginButtonStyle]"
				class="loginBtn"
				v-if="showPasswordLogin">
				登录
			</button>
			<view class="alternative">
				<view class="password" @tap="toggleLoginType">
					{{ showPasswordLogin ? '验证码登录' : '密码登录' }}
				</view>
				<view class="issue">遇到问题</view>
			</view>
		</view>
		<view class="buttom">
			<view class="loginType">
				<view class="wechat item">
					<view class="icon"><uni-icons size="40" type="weixin" color="rgb(83,194,64)"></uni-icons></view>
					微信
				</view>
				<view class="QQ item">
					<view class="icon"><uni-icons size="40" type="qq" color="rgb(17,183,233)"></uni-icons></view>
					QQ
				</view>
			</view>
			<view class="hint">
				登录代表同意
				<text class="link">用户协议、隐私政策，</text>
				并授权使用您的账号信息（如昵称、头像、收获地址）以便您统一管理
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { getCurrentInstance } from "vue";
import { getToken } from '@/utils/auth';
import { getSmsCaptcha } from '@/api/login';
import { useUserStore } from '@/store';

const { proxy } = getCurrentInstance();
const tel = ref('');
const username = ref('');
const password = ref('');
const code = ref('');
const showPasswordLogin = ref(false);
const showCodeInput = ref(false);
const countDown = ref(0);
let timer = null;

// 计算属性 - 输入样式
const inputStyle = computed(() => {
	let style = {};
	if (tel.value) {
		style.color = "#fff";
		style.backgroundColor = '#f9c00c';
	}
	return style;
});

// 计算属性 - 登录按钮样式
const loginButtonStyle = computed(() => {
	let style = {};
	if (showPasswordLogin.value && username.value && password.value) {
		style.color = "#fff";
		style.backgroundColor = '#f9c00c';
	}
	return style;
});

// 计算属性 - 验证码按钮样式
const codeButtonStyle = computed(() => {
	let style = {};
	if (code.value && code.value.length === 6) {
		style.color = "#fff";
		style.backgroundColor = '#f9c00c';
	}
	return style;
});

// 切换登录类型
const toggleLoginType = () => {
	showPasswordLogin.value = !showPasswordLogin.value;
	// 切换登录类型时隐藏验证码输入
	showCodeInput.value = false;
	code.value = '';
};

// 获取短信验证码
const getSmsCode = () => {
	if (!tel.value) {
		proxy.$modal.msgError('请输入手机号');
		return;
	}
	if (!/^1[3-9]\d{9}$/.test(tel.value)) {
		proxy.$modal.msgError('请输入正确的手机号');
		return;
	}
	
	// 调用获取短信验证码的API
	getSmsCaptcha(tel.value).then(res => {
		proxy.$modal.msgSuccess('验证码已发送');
		// 显示验证码输入区域
		showCodeInput.value = true;
	}).catch(err => {
		proxy.$modal.msgError('验证码发送失败');
		// 即使失败也显示验证码输入区域，用户可以重试
		showCodeInput.value = true;
	});
	
	// 倒计时
	countDown.value = 60;
	if (timer) clearInterval(timer);
	timer = setInterval(() => {
		countDown.value--;
		if (countDown.value <= 0) {
			clearInterval(timer);
		}
	}, 1000);
};

// 提交验证码登录
const submitCode = async () => {
	if (!code.value || code.value.length !== 6) {
		proxy.$modal.msgError('请输入6位验证码');
		return;
	}
	
	proxy.$modal.loading("登录中，请耐心等待...");
	try {
		// 调用短信登录接口
		await useUserStore().smsLogin(tel.value, code.value);
		proxy.$modal.closeLoading();
		
		// 获取用户信息并跳转
		await useUserStore().getInfo();
		proxy.$tab.reLaunch('/pages/index');
	} catch (error) {
		proxy.$modal.closeLoading();
		proxy.$modal.msgError(error.message || '登录失败');
	}
};

// 提交登录（密码登录）
const submit = async () => {
	if (showPasswordLogin.value) {
		// 密码登录
		if (!username.value) {
			proxy.$modal.msgError('请输入账号或手机号');
			return;
		}
		if (!password.value) {
			proxy.$modal.msgError('请输入密码');
			return;
		}
		
		proxy.$modal.loading("登录中，请耐心等待...");
		try {
			// 使用现有登录接口，支持账号或手机号登录
			await useUserStore().login({
				username: username.value,
				password: password.value,
				rememberMe: false
			});
			proxy.$modal.closeLoading();
			
			// 获取用户信息并跳转
			await useUserStore().getInfo();
			proxy.$tab.reLaunch('/pages/index');
		} catch (error) {
			proxy.$modal.closeLoading();
			proxy.$modal.msgError(error.message || '登录失败');
		}
	} else {
		// 短信验证码登录 - 如果还没有获取验证码，提示获取
		if (!showCodeInput.value) {
			proxy.$modal.msgError('请先获取短信验证码');
			return;
		}
		// 如果已经显示验证码输入，提示输入验证码
		if (!code.value || code.value.length !== 6) {
			proxy.$modal.msgError('请输入6位验证码');
			return;
		}
		// 调用验证码登录
		await submitCode();
	}
};
</script>

<style lang="scss" scoped>
.wrap {
	font-size: 28rpx;

	.content {
		width: 600rpx;
		margin: 80rpx auto 0;

		.title {
			text-align: left;
			font-size: 60rpx;
			font-weight: 500;
			margin-bottom: 100rpx;
		}

		input {
			text-align: left;
			margin-bottom: 10rpx;
			padding-bottom: 6rpx;
		}

		.tips {
			color: $uni-text-color-grey;
			margin-bottom: 60rpx;
			margin-top: 8rpx;
			font-size: 12px;
		}

		// 验证码输入区域
		.code-section {
			margin-top: 20rpx;
			
			.code-tips {
				color: $uni-text-color-grey;
				font-size: 24rpx;
				margin-bottom: 40rpx;
				text-align: center;
			}
			
			.code-input {
				text-align: center;
				font-size: 36rpx;
				letter-spacing: 10rpx;
				margin-bottom: 40rpx;
			}
			
			.verify-btn {
				background-color: rgb(253, 243, 208);
				color: $uni-text-color-disable;
				border: none;
				font-size: 30rpx;
				padding: 12rpx 0;
				margin-top: 20rpx;
				width: 100%;
				
				&[disabled] {
					opacity: 0.6;
				}
				
				&::after {
					border: none;
				}
			}
			
			.resend-tips {
				color: $uni-text-color-grey;
				font-size: 24rpx;
				text-align: center;
				margin-top: 30rpx;
				
				.resend-link {
					color: #f9c00c;
					margin-left: 10rpx;
				}
				
				.resend-countdown {
					color: $uni-text-color-disable;
					margin-left: 10rpx;
				}
			}
		}

		.getCaptcha {
			background-color: rgb(253, 243, 208);
			color: $uni-text-color-disable;
			border: none;
			font-size: 30rpx;
			padding: 12rpx 0;
			margin-top: 20rpx;

			&::after {
				border: none;
			}
		}
		
		.loginBtn {
			background-color: rgb(253, 243, 208);
			color: $uni-text-color-disable;
			border: none;
			font-size: 30rpx;
			padding: 12rpx 0;
			margin-top: 40rpx;
			width: 100%;

			&::after {
				border: none;
			}
		}

		.alternative {
			color: $uni-text-color-disable;
			display: flex;
			justify-content: space-between;
			margin-top: 30rpx;
		}
	}

	.buttom {
		.loginType {
			display: flex;
			padding: 350rpx 150rpx 150rpx 150rpx;
			justify-content: space-between;

			.item {
				display: flex;
				flex-direction: column;
				align-items: center;
				color: $uni-text-color;
				font-size: 28rpx;
			}
		}

		.hint {
			padding: 20rpx 40rpx;
			font-size: 20rpx;
			color: $uni-text-color-grey;

			.link {
				color: #f9c00c;
			}
		}
	}
}
</style>