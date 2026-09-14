"""Minimal client for the nexus-shop (CRMEB-on-RuoYi) admin API.

Auth: RuoYi-Vue-Plus Sa-Token.  POST /auth/login -> access_token,
then `Authorization: Bearer <token>` on every admin call.

Contract notes verified against the running app's source:
  * /api/admin/upload/image  multipart field name is `multipart` (NOT `file`)
  * /api/admin/store/product/save   returns CommonResult<String>, NOT the new id
  * save() forces isShow=false, so putOnShell/{id} is mandatory afterwards
  * save() dereferences request.content -> must send "" instead of null
"""
from __future__ import annotations

import json
from typing import Any

import requests

DEFAULT_BASE = "http://127.0.0.1:20400"
CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e"
TENANT_ID = "000000"


class MallError(RuntimeError):
    pass


class MallClient:
    def __init__(self, base: str = DEFAULT_BASE, token: str | None = None):
        self.base = base.rstrip("/")
        self.token = token
        self.s = requests.Session()

    # ---------------- auth ----------------
    def login(self, username: str = "admin", password: str = "admin123",
              client_id: str = CLIENT_ID, tenant_id: str = TENANT_ID) -> str:
        r = self.s.post(f"{self.base}/auth/login", json={
            "tenantId": tenant_id,
            "username": username,
            "password": password,
            "clientId": client_id,
            "grantType": "password",
        }, timeout=30)
        body = self._decode(r)
        if body.get("code") != 200:
            raise MallError(f"login failed: {body}")
        data = body["data"]
        self.token = data.get("access_token") or data.get("accessToken")
        if not self.token:
            raise MallError(f"no access_token in {data}")
        return self.token

    # ---------------- plumbing ----------------
    @staticmethod
    def _decode(r: requests.Response) -> dict:
        try:
            return r.json()
        except Exception:
            raise MallError(f"non-JSON response {r.status_code}: {r.text[:400]}")

    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    def get(self, path: str, **params) -> Any:
        r = self.s.get(f"{self.base}{path}", params=params, headers=self._headers(), timeout=60)
        body = self._decode(r)
        if body.get("code") != 200:
            raise MallError(f"GET {path} -> {body}")
        return body.get("data")

    def post(self, path: str, payload: dict | None = None) -> Any:
        r = self.s.post(f"{self.base}{path}", data=json.dumps(payload or {}, ensure_ascii=False).encode("utf-8"),
                        headers=self._headers(), timeout=120)
        body = self._decode(r)
        if body.get("code") != 200:
            raise MallError(f"POST {path} -> {body}")
        return body.get("data")

    def post_form(self, path: str, payload: dict) -> Any:
        """Some admin endpoints bind a @Validated DTO from form/query params, not JSON."""
        h = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        r = self.s.post(f"{self.base}{path}", data=payload, headers=h, timeout=120)
        body = self._decode(r)
        if body.get("code") != 200:
            raise MallError(f"POST(form) {path} {payload} -> {body}")
        return body.get("data")

    def raw_post(self, path: str, payload: dict) -> dict:
        """Like post() but returns the whole envelope (for endpoints that fail softly)."""
        r = self.s.post(f"{self.base}{path}", data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                        headers=self._headers(), timeout=120)
        return self._decode(r)

    # ---------------- upload ----------------
    def upload_image(self, path, model: str = "product", pid: int = 0) -> str:
        """Upload one image; returns the relative url the product API expects."""
        with open(path, "rb") as fh:
            r = self.s.post(
                f"{self.base}/api/admin/upload/image",
                params={"model": model, "pid": pid},
                files={"multipart": (path.name, fh, "image/png")},
                headers={"Authorization": f"Bearer {self.token}"} if self.token else {},
                timeout=180,
            )
        body = self._decode(r)
        if body.get("code") != 200:
            raise MallError(f"upload {path.name} -> {body}")
        return body["data"]["url"]


if __name__ == "__main__":
    c = MallClient()
    tok = c.login()
    print("token:", tok[:40], "...")
    print("categories:", len(c.get("/api/admin/category/list/tree", type=1, status=-1) or []))
