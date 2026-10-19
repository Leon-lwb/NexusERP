using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;

namespace Nexus.EngineeringConnector {
    /// <summary>分发文件与当前用户数据分离，不在程序目录写配置或身份。</summary>
    internal sealed class ConnectorPaths {
        internal readonly string ApplicationRoot, UserRoot;
        internal ConnectorPaths(string applicationRoot, string userRoot) { ApplicationRoot = Path.GetFullPath(applicationRoot); UserRoot = Path.GetFullPath(userRoot); }
        internal string Node { get { return Path.Combine(ApplicationRoot, "runtime", "node.exe"); } }
        internal string Cli { get { return Path.Combine(ApplicationRoot, "dist", "cli.js"); } }
        internal string Config { get { return Path.Combine(UserRoot, "config.json"); } }
        internal string Workspace { get { return Path.Combine(UserRoot, "workspace"); } }
        internal bool HasIdentity { get { return File.Exists(Path.Combine(Workspace, "identity.dpapi")); } }
        internal static ConnectorPaths Current() {
            return new ConnectorPaths(AppDomain.CurrentDomain.BaseDirectory, Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Nexus", "EngineeringConnector"));
        }
    }
    /// <summary>与CLI保持一致的白名单配置；不保存配对码或设备访问凭证。</summary>
    internal sealed class ConnectorSettings {
        public string protocolVersion { get; set; }
        public string serverUrl { get; set; }
        public string executable { get; set; }
        public string workspaceRoot { get; set; }
        public bool development { get; set; }
        public ConnectorSettings() { protocolVersion = "nexus-engineering/1"; serverUrl = ""; executable = ""; workspaceRoot = ""; }
        internal static ConnectorSettings Parse(string json) {
            var serializer = new JavaScriptSerializer();
            var values = serializer.Deserialize<Dictionary<string, object>>(json);
            var allowed = new HashSet<string>(new[] { "protocolVersion", "serverUrl", "executable", "workspaceRoot", "development" });
            if (values == null) throw new ArgumentException("配置必须是对象。");
            foreach (var key in values.Keys) if (!allowed.Contains(key)) throw new ArgumentException("配置包含不支持的字段。");
            var value = serializer.Deserialize<ConnectorSettings>(json);
            if (value.protocolVersion != "nexus-engineering/1") throw new ArgumentException("配置协议版本不匹配。");
            return value;
        }
        internal static ConnectorSettings Load(ConnectorPaths paths) {
            if (new FileInfo(paths.Config).Length > 16384) throw new ArgumentException("配置文件过大。");
            return Parse(File.ReadAllText(paths.Config, Encoding.UTF8));
        }
        internal void Validate() {
            Uri address;
            if (!Uri.TryCreate((serverUrl ?? "").Trim(), UriKind.Absolute, out address) || address.UserInfo.Length != 0 || address.Query.Length != 0 || address.Fragment.Length != 0)
                throw new ArgumentException("请填写不含账号密码、查询参数或片段的 Nexus 地址。");
            if (address.Scheme != "https" && address.Scheme != "http")
                throw new ArgumentException("Nexus 地址只支持 HTTP 或 HTTPS。");
            if (string.IsNullOrWhiteSpace(executable) || !Path.IsPathRooted(executable) || !File.Exists(executable))
                throw new ArgumentException("请选择已安装的嘉立创 EDA 专业版 EXE。");
            if (string.IsNullOrWhiteSpace(workspaceRoot) || !Path.IsPathRooted(workspaceRoot)) throw new ArgumentException("工作目录无效。");
            serverUrl = address.AbsoluteUri.TrimEnd('/');
            executable = Path.GetFullPath(executable);
        }
        internal void Save(ConnectorPaths paths) {
            workspaceRoot = paths.Workspace;
            Validate();
            Directory.CreateDirectory(paths.UserRoot);
            var temporary = paths.Config + ".tmp";
            File.WriteAllText(temporary, new JavaScriptSerializer().Serialize(this), new UTF8Encoding(false));
            if (File.Exists(paths.Config)) File.Replace(temporary, paths.Config, null); else File.Move(temporary, paths.Config);
        }
    }
    /// <summary>按Windows进程argv规则引用参数，始终通过无Shell的ProcessStartInfo调用。</summary>
    internal static class CommandLine {
        internal static string Quote(string value) {
            var result = new StringBuilder("\"");
            var slashes = 0;
            foreach (var character in value) {
                if (character == '\\') { slashes++; continue; }
                result.Append('\\', character == '"' ? slashes * 2 + 1 : slashes);
                result.Append(character); slashes = 0;
            }
            result.Append('\\', slashes * 2); result.Append('"'); return result.ToString();
        }
        internal static string Join(string[] values) { return string.Join(" ", Array.ConvertAll(values, Quote)); }
    }
}
