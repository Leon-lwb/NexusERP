using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace Nexus.EngineeringConnector {
    /// <summary>隔离自测：只使用临时文件和假工作进程，不配对真实服务。</summary>
    internal static class SelfTests {
        private static void Check(bool condition, string name, List<string> checks) {
            if (!condition) throw new InvalidOperationException(name);
            checks.Add(name);
        }
        private static bool Rejects(Action action) {
            try { action(); return false; } catch (ArgumentException) { return true; }
        }
        private static async Task<T> Bounded<T>(Task<T> task) {
            if (await Task.WhenAny(task, Task.Delay(8000)) != task) throw new Exception("假工作进程等待超时");
            return await task;
        }
        internal static async Task<int> Run(string resultPath, string applicationRoot) {
            var checks = new List<string>();
            var temporary = Path.Combine(Path.GetTempPath(), "Nexus 桌面自测 " + Guid.NewGuid().ToString("N"));
            string failed = null;
            Directory.CreateDirectory(temporary);
            try {
                var instanceName = "Local\\Nexus.Desktop.Test." + Guid.NewGuid().ToString("N");
                using (var first = Program.AcquireInstance(instanceName)) {
                    Check(first != null, "首个实例取得互斥所有权", checks);
                    var duplicate = Task.Run(() => Program.AcquireInstance(instanceName)).GetAwaiter().GetResult();
                    Check(duplicate == null, "重复实例不能占用互斥所有权", checks);
                    using (var staleHandle = new System.Threading.Mutex(false, instanceName)) {
                        first.ReleaseMutex();
                        var reacquired = Task.Run(() => {
                            using (var next = Program.AcquireInstance(instanceName)) {
                                if (next == null) return false;
                                next.ReleaseMutex(); return true;
                            }
                        }).GetAwaiter().GetResult();
                        Check(reacquired, "旧提示句柄存在时仍能重新启动", checks);
                    }
                }
                var paths = new ConnectorPaths(applicationRoot, temporary);
                var executable = Path.Combine(temporary, "嘉立创 测试.exe");
                File.WriteAllText(executable, "fixture");
                var settings = new ConnectorSettings { serverUrl = "https://nexus.example.test/", executable = executable, workspaceRoot = paths.Workspace };
                settings.Validate();
                Check(settings.serverUrl == "https://nexus.example.test", "HTTPS地址规范化", checks);
                settings.serverUrl = "https://user:secret@nexus.example.test";
                Check(Rejects(() => settings.Validate()), "拒绝URL凭证", checks);
                settings.development = false;
                foreach (var address in new[] { "http://10.0.0.1:8080", "http://nexus.example.test:8080/api", "http://127.0.0.1:18080", "http://[::1]:8080" }) {
                    settings.serverUrl = address; settings.Validate();
                    Check(new Uri(settings.serverUrl).Equals(new Uri(address)), "普通HTTP地址可用：" + address, checks);
                }
                settings.serverUrl = "ftp://nexus.example.test";
                Check(Rejects(() => settings.Validate()), "拒绝HTTP以外协议", checks);
                settings.serverUrl = "http://user:secret@nexus.example.test";
                Check(Rejects(() => settings.Validate()), "HTTP仍拒绝URL凭证", checks);
                settings.serverUrl = "https://nexus.example.test";
                settings.Save(paths);
                Check(ConnectorSettings.Load(paths).executable == executable, "中文空格配置往返", checks);
                Check(Rejects(() => ConnectorSettings.Parse("{\"refreshToken\":\"forbidden\"}")), "配置拒绝凭证和未知字段", checks);
                Check(Rejects(() => ConnectorSettings.Parse("{\"protocolVersion\":\"other/2\"}")), "拒绝协议不匹配", checks);
                var fixture = Path.Combine(temporary, "fake worker.mjs");
                File.WriteAllText(fixture, "import{createInterface}from'node:readline';const r=createInterface({input:process.stdin});const c=process.argv[2];if(c==='run'){console.log('READY');r.on('line',x=>{if(x==='stop')process.exit(0)});r.on('close',()=>process.exit(0))}else if(c==='pair'){r.on('line',x=>{console.log(x==='one-time-code'?'PAIRED':'BAD_INPUT');process.exit(x==='one-time-code'?0:2)})}else{console.log(JSON.stringify({connected:true,config:process.argv[4]}));r.close()}", new UTF8Encoding(false));
                using (var worker = new ConnectorProcess(paths, fixture)) {
                    var previousOptions = Environment.GetEnvironmentVariable("NODE_OPTIONS");
                    var previousPath = Environment.GetEnvironmentVariable("NODE_PATH");
                    try {
                        Environment.SetEnvironmentVariable("NODE_OPTIONS", "--require=nexus-missing-desktop-test-module");
                        Environment.SetEnvironmentVariable("NODE_PATH", Path.Combine(temporary, "missing-modules"));
                        var isolated = await Bounded(worker.RunAsync("doctor", null));
                        Check(isolated.ExitCode == 0, "内置Node不继承机器预加载选项", checks);
                    } finally {
                        Environment.SetEnvironmentVariable("NODE_OPTIONS", previousOptions);
                        Environment.SetEnvironmentVariable("NODE_PATH", previousPath);
                    }
                    var diagnostic = await Bounded(worker.RunAsync("doctor", null));
                    Check(diagnostic.ExitCode == 0 && diagnostic.Output.Contains("connected"), "读取假诊断进程结果", checks);
                    var diagnosticData = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(diagnostic.Output);
                    Check((string)diagnosticData["config"] == paths.Config, "中文空格参数无损传递", checks);
                    var pair = await Bounded(worker.RunAsync("pair", "one-time-code"));
                    Check(pair.ExitCode == 0 && pair.Output.Contains("PAIRED") && !pair.Output.Contains("one-time-code"), "配对码只走标准输入", checks);
                    var ready = new TaskCompletionSource<bool>();
                    worker.Line += line => { if (line == "READY") ready.TrySetResult(true); };
                    var running = worker.RunAsync("run", null);
                    await Bounded(ready.Task);
                    Check(worker.IsRunning, "后台工作进程已启动", checks);
                    bool duplicateRejected = false;
                    try { worker.RunAsync("run", null); } catch (InvalidOperationException) { duplicateRejected = true; }
                    Check(duplicateRejected, "同一启动器拒绝并行工作者", checks);
                    await worker.StopAsync();
                    var ended = await Bounded(running);
                    Check(ended.ExitCode == 0 && !ended.Forced && !worker.IsRunning, "正常停止不强杀进程", checks);
                    ready = new TaskCompletionSource<bool>();
                    running = worker.RunAsync("run", null);
                    await Bounded(ready.Task);
                    worker.CloseInput();
                    ended = await Bounded(running);
                    Check(ended.ExitCode == 0 && !worker.IsRunning, "父输入关闭后工作者退出", checks);
                }
            } catch (Exception error) { failed = error.GetType().Name + ": " + error.Message; }
            var report = new JavaScriptSerializer().Serialize(new { checks = checks, failed = failed });
            File.WriteAllText(resultPath, report, new UTF8Encoding(false));
            return failed == null ? 0 : 1;
        }
        private static void AssertLayout(System.Windows.Forms.Control parent) {
            foreach (System.Windows.Forms.Control child in parent.Controls) {
                if (!child.Visible) continue;
                if (!parent.ClientRectangle.Contains(child.Bounds)) throw new InvalidOperationException("窗口控件超出容器边界：" + child.GetType().Name);
                AssertLayout(child);
            }
        }
        private static void PumpUntil(Task task) {
            var clock = Stopwatch.StartNew();
            while (!task.IsCompleted && clock.ElapsedMilliseconds < 8000) {
                System.Windows.Forms.Application.DoEvents(); System.Threading.Thread.Sleep(10);
            }
            if (!task.IsCompleted) throw new InvalidOperationException("桌面停止测试超时。");
            task.GetAwaiter().GetResult();
        }
        private static void AssertStopping(ConnectorPaths paths) {
            Directory.CreateDirectory(paths.UserRoot);
            var fixture = Path.Combine(paths.UserRoot, "slow-stop-worker.mjs");
            File.WriteAllText(fixture, "import{createInterface}from'node:readline';const r=createInterface({input:process.stdin});console.log('READY');r.on('line',x=>{if(x==='stop')setTimeout(()=>process.exit(0),300)});r.on('close',()=>{})", new UTF8Encoding(false));
            using (var worker = new ConnectorProcess(paths, fixture))
            using (var form = new MainForm(paths, false, worker)) {
                var ready = new TaskCompletionSource<bool>();
                worker.Line += line => { if (line == "READY") ready.TrySetResult(true); };
                var running = worker.RunAsync("run", null);
                PumpUntil(ready.Task);
                var method = typeof(MainForm).GetMethod("StopWorker", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic);
                var first = (Task)method.Invoke(form, new object[0]);
                try {
                    var second = (Task)method.Invoke(form, new object[0]);
                    if (!object.ReferenceEquals(first, second)) throw new InvalidOperationException("退出必须等待正在执行的停止任务。");
                    var exit = (Task)typeof(MainForm).GetMethod("ExitApplication", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic).Invoke(form, new object[0]);
                    if (exit.IsCompleted) throw new InvalidOperationException("工作者停止前不能关闭应用。");
                    PumpUntil(exit);
                    if (worker.IsRunning) throw new InvalidOperationException("退出后工作者仍在运行。");
                } finally { PumpUntil(running); PumpUntil(first); }
            }
        }
        private static void AssertSessionClosing(ConnectorPaths paths) {
            using (var sessionForm = new MainForm(paths, true)) {
                var trayField = typeof(MainForm).GetField("tray", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic);
                ((System.Windows.Forms.NotifyIcon)trayField.GetValue(sessionForm)).Visible = false;
                var closing = new System.Windows.Forms.FormClosingEventArgs(System.Windows.Forms.CloseReason.WindowsShutDown, false);
                typeof(System.Windows.Forms.Form).GetMethod("OnFormClosing", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic).Invoke(sessionForm, new object[] { closing });
                if (closing.Cancel) throw new InvalidOperationException("窗口不能阻止Windows会话退出。");
            }
        }
        internal static void RenderPreview(string resultPath, string applicationRoot) {
            var paths = new ConnectorPaths(applicationRoot, Path.Combine(Path.GetTempPath(), "Nexus-preview-" + Guid.NewGuid().ToString("N")));
            using (var form = new MainForm(paths, false)) {
                form.ShowInTaskbar = false;
                form.StartPosition = System.Windows.Forms.FormStartPosition.Manual;
                form.Location = new Point(-20000, -20000);
                form.Show();
                System.Windows.Forms.Application.DoEvents();
                form.PerformLayout();
                AssertLayout(form);
                using (var image = new Bitmap(form.Width, form.Height)) {
                    form.DrawToBitmap(image, new Rectangle(0, 0, image.Width, image.Height));
                    image.Save(Path.ChangeExtension(resultPath, ".png"), ImageFormat.Png);
                    var colors = new HashSet<int>();
                    for (var y = 80; y < image.Height - 60; y += 4)
                        for (var x = 24; x < image.Width - 24; x += 4) colors.Add(image.GetPixel(x, y).ToArgb());
                    if (colors.Count < 20) throw new InvalidOperationException("窗口内容未绘制，预览为空。");
                    AssertSessionClosing(paths);
                    AssertStopping(paths);
                }
            }
        }
    }
}
