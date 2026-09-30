"""本地全流程守卫的纯校验与参数辅助函数。"""

from __future__ import annotations


_LOOPBACK_HOSTS = frozenset({'localhost', '127.0.0.1'})


def validate_runtime_args(host: str, port: int, profile: str) -> list[str]:
    """返回本地只读预检契约的校验错误。"""
    errors: list[str] = []
    normalized_host = host.strip().lower()
    normalized_profile = profile.strip().lower()

    if normalized_host not in _LOOPBACK_HOSTS:
        errors.append('database host must be localhost or 127.0.0.1')
    if port != 3306:
        errors.append('database port must be TCP 3306')
    if normalized_profile != 'dev':
        errors.append('profile must be dev')

    return errors


def build_backend_args(port: int = 8080, *, disable_captcha: bool = False) -> list[str]:
    """构建进程参数，不修改任何应用配置文件。"""
    args = [
        '--spring.profiles.active=dev',
        f'--server.port={port}',
    ]
    if disable_captcha:
        args.append('--captcha.enable=false')
    return args
