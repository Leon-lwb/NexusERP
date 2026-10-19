using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading.Tasks;

namespace Nexus.EngineeringConnector {
    /// <summary>正常停止与超时终止分别呈现。</summary>
    internal sealed class CommandResult {
        internal int ExitCode;
        internal bool Forced;
        internal string Output;
    }
    /// <summary>只管理自身创建的Node工作者，不枚举或结束EDA及其他用户进程。</summary>
    internal sealed class ConnectorProcess : IDisposable {
        private readonly ConnectorPaths paths;
        private readonly string cli;
        private readonly object gate = new object();
        private Process process;
        private Task<CommandResult> completion;
        private bool forced;
        internal event Action<string> Line;
        internal ConnectorProcess(ConnectorPaths paths, string testCli = null) { this.paths = paths; cli = testCli ?? paths.Cli; }
        internal bool IsRunning { get { lock (gate) { return process != null; } } }
        internal Task<CommandResult> RunAsync(string command, string input) {
            if (command != "run" && command != "doctor" && command != "pair" && command != "unpair") throw new ArgumentException("不支持的命令。");
            lock (gate) {
                if (process != null) throw new InvalidOperationException("连接器已有操作正在运行。");
                if (!File.Exists(paths.Node) || !File.Exists(cli)) throw new FileNotFoundException("分发文件不完整，请完整解压应用包。");
                var arguments = (command == "run" || command == "doctor" || command == "pair") ? new[] { cli, command, "--config", paths.Config, "--desktop" } : new[] { cli, command, "--config", paths.Config };
                var start = new ProcessStartInfo(paths.Node, CommandLine.Join(arguments)) {
                    WorkingDirectory = paths.ApplicationRoot, UseShellExecute = false, CreateNoWindow = true,
                    RedirectStandardInput = true, RedirectStandardOutput = true, RedirectStandardError = true,
                    StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8
                };
                // 使用包内运行环境，不加载机器级Node预加载模块；保留系统证书及代理配置。
                start.EnvironmentVariables.Remove("NODE_OPTIONS");
                start.EnvironmentVariables.Remove("NODE_PATH");
                var output = new StringBuilder();
                var done = new TaskCompletionSource<CommandResult>();
                var current = new Process { StartInfo = start, EnableRaisingEvents = true };
                forced = false;
                DataReceivedEventHandler receive = (sender, data) => {
                    if (data.Data == null) return;
                    var line = data.Data.Length > 2000 ? data.Data.Substring(0, 2000) + "…" : data.Data;
                    lock (output) { if (output.Length < 64000) output.AppendLine(line); }
                    var handler = Line; if (handler != null) handler(line);
                };
                current.OutputDataReceived += receive;
                current.ErrorDataReceived += receive;
                current.Exited += (sender, args) => Task.Run(() => {
                    current.WaitForExit();
                    CommandResult result;
                    lock (gate) {
                        result = new CommandResult { ExitCode = current.ExitCode, Forced = forced, Output = output.ToString() };
                        if (process == current) process = null;
                    }
                    current.Dispose();
                    done.TrySetResult(result);
                });
                try {
                    current.Start(); process = current; completion = done.Task;
                    current.BeginOutputReadLine(); current.BeginErrorReadLine();
                    if (input != null) { current.StandardInput.WriteLine(input); current.StandardInput.Flush(); }
                    return done.Task;
                } catch {
                    process = null;
                    try { if (!current.HasExited) current.Kill(); } catch (InvalidOperationException) {}
                    current.Dispose(); throw;
                }
            }
        }
        internal void CloseInput() {
            lock (gate) { if (process != null) { try { process.StandardInput.Close(); } catch (IOException) {} catch (InvalidOperationException) {} } }
        }
        internal async Task StopAsync() {
            Process current; Task<CommandResult> pending;
            lock (gate) {
                current = process; pending = completion;
                if (current == null) return;
                try { current.StandardInput.WriteLine("stop"); current.StandardInput.Flush(); current.StandardInput.Close(); }
                catch (IOException) {} catch (InvalidOperationException) {}
            }
            if (await Task.WhenAny(pending, Task.Delay(10000)) != pending) {
                lock (gate) {
                    if (process == current) {
                        forced = true;
                        try { current.Kill(); } catch (InvalidOperationException) {}
                    }
                }
            }
            await pending;
        }
        public void Dispose() { CloseInput(); }
    }
}
