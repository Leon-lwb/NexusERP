using System;
using System.Collections.Generic;
using System.Drawing;
using System.Diagnostics;
using System.IO;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Web.Script.Serialization;

namespace Nexus.EngineeringConnector {
    /// <summary>当前用户的配置、配对与运行窗口，关闭窗口后可从托盘恢复。</summary>
    internal sealed class MainForm : Form {
        private readonly ConnectorPaths paths;
        private readonly ConnectorProcess worker;
        private readonly bool autoStart;
        private readonly TextBox server = new TextBox(), editor = new TextBox(), pairing = new TextBox(), logs = new TextBox();
        private bool legacyDevelopment;
        private readonly Label status = new Label();
        private readonly Button save = new Button(), doctor = new Button(), pair = new Button(), unpair = new Button(), start = new Button(), stop = new Button(), browse = new Button();
        private readonly Queue<string> lines = new Queue<string>();
        private readonly NotifyIcon tray;
        private readonly ToolStripMenuItem trayStart, trayStop;
        private bool busy, allowClose, exiting, stopping;
        private string activeCommand;
        private Task stopTask;
        private readonly Color blue = Color.FromArgb(31, 94, 216), ink = Color.FromArgb(31, 45, 68);
        internal MainForm(ConnectorPaths paths, bool autoStart, ConnectorProcess testWorker = null) {
            this.paths = paths; this.autoStart = autoStart;
            worker = testWorker ?? new ConnectorProcess(paths);
            worker.Line += line => OnUi(() => AppendLog(line));
            Text = "Nexus 工程连接器";
            Font = new Font("Microsoft YaHei UI", 9F);
            ClientSize = new Size(880, 690); MinimumSize = new Size(850, 690);
            StartPosition = FormStartPosition.CenterScreen;
            AutoScaleMode = AutoScaleMode.Font; BackColor = Color.FromArgb(245, 247, 251);
            Icon = SystemIcons.Application;
            var root = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(24, 18, 24, 12), ColumnCount = 1, RowCount = 5 };
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 78));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 260));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 36));
            Controls.Add(root);
            var header = new Panel { Dock = DockStyle.Fill };
            header.Controls.Add(new Label { Text = "Nexus 工程连接器", Font = new Font(Font.FontFamily, 20F, FontStyle.Bold), ForeColor = ink, Location = new Point(0, 0), AutoSize = true });
            header.Controls.Add(new Label { Text = "连接嘉立创 EDA，接收工程任务并回传设计产物", ForeColor = Color.DimGray, Location = new Point(2, 43), AutoSize = true });
            status.Text = "待配置"; status.ForeColor = blue; status.TextAlign = ContentAlignment.MiddleRight;
            status.Width = 225; status.Dock = DockStyle.Right;
            header.Controls.Add(status); root.Controls.Add(header, 0, 0);
            var configuration = new GroupBox { Text = "连接设置", Dock = DockStyle.Fill, Padding = new Padding(16), BackColor = Color.White, ForeColor = ink };
            var fields = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 4, RowCount = 5 };
            fields.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 115));
            fields.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            fields.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 92));
            fields.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 92));
            foreach (var height in new[] { 42, 42, 34, 42, 46 }) fields.RowStyles.Add(new RowStyle(SizeType.Absolute, height));
            AddLabel(fields, "Nexus 服务地址", 0); fields.Controls.Add(server, 1, 0); fields.SetColumnSpan(server, 3);
            AddLabel(fields, "嘉立创专业版", 1); fields.Controls.Add(editor, 1, 1); fields.SetColumnSpan(editor, 2);
            ConfigureButton(browse, "浏览…"); fields.Controls.Add(browse, 3, 1);
            var addressHelp = new Label { Text = "支持 HTTP/HTTPS；站点地址会自动识别工程 API。", Dock = DockStyle.Fill, ForeColor = Color.DimGray, Margin = new Padding(3, 6, 0, 0) };
            fields.Controls.Add(addressHelp, 1, 2); fields.SetColumnSpan(addressHelp, 3);
            AddLabel(fields, "一次性配对码", 3); pairing.UseSystemPasswordChar = true; fields.Controls.Add(pairing, 1, 3);
            ConfigureButton(pair, "配对设备"); fields.Controls.Add(pair, 2, 3);
            ConfigureButton(unpair, "解除绑定"); fields.Controls.Add(unpair, 3, 3);
            var help = new Label { Text = "首次使用：填写地址并选择 EDA，在 Nexus“工程设备”页生成配对码后配对。\n配置与工程文件保存在当前 Windows 用户目录。", Dock = DockStyle.Fill, ForeColor = Color.DimGray, Margin = new Padding(3, 3, 0, 0) };
            fields.Controls.Add(help, 0, 4); fields.SetColumnSpan(help, 4);
            foreach (var field in new[] { server, editor, pairing }) { field.Dock = DockStyle.Fill; field.Margin = new Padding(3, 5, 6, 6); }
            configuration.Controls.Add(fields); root.Controls.Add(configuration, 0, 1);
            var actions = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.LeftToRight, Padding = new Padding(0, 10, 0, 6), WrapContents = false };
            ConfigureButton(save, "保存设置"); ConfigureButton(doctor, "检查 EDA"); ConfigureButton(start, "启动连接器"); ConfigureButton(stop, "停止");
            start.Width = 126; start.BackColor = blue; start.ForeColor = Color.White;
            var open = new Button(); ConfigureButton(open, "打开工作目录"); open.Width = 130;
            actions.Controls.AddRange(new Control[] { save, doctor, start, stop, open }); root.Controls.Add(actions, 0, 2);
            var logGroup = new GroupBox { Text = "运行记录", Dock = DockStyle.Fill, Padding = new Padding(14), BackColor = Color.White, ForeColor = ink };
            logs.Multiline = true; logs.ReadOnly = true; logs.ScrollBars = ScrollBars.Vertical; logs.Dock = DockStyle.Fill;
            logs.BackColor = Color.White; logs.BorderStyle = BorderStyle.None; logs.Font = new Font("Microsoft YaHei UI", 9F); logs.ForeColor = ink;
            logGroup.Controls.Add(logs); root.Controls.Add(logGroup, 0, 3);
            root.Controls.Add(new Label { Text = "首次配对后，后续打开应用会自动启动。关闭窗口后可从系统托盘打开。", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(0, 12, 0, 0) }, 0, 4);
            var menu = new ContextMenuStrip();
            menu.Items.Add("打开窗口", null, (sender, args) => RestoreWindow());
            trayStart = new ToolStripMenuItem("启动连接器", null, async (sender, args) => await RunOperation("run"));
            trayStop = new ToolStripMenuItem("停止连接器", null, async (sender, args) => await StopWorker());
            menu.Items.Add(trayStart); menu.Items.Add(trayStop); menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("退出应用", null, async (sender, args) => await ExitApplication());
            tray = new NotifyIcon { Icon = Icon, Text = "Nexus 工程连接器", ContextMenuStrip = menu, Visible = autoStart };
            tray.DoubleClick += (sender, args) => RestoreWindow();
            browse.Click += (sender, args) => {
                using (var dialog = new OpenFileDialog { Title = "选择嘉立创 EDA 专业版", Filter = "Windows 应用程序 (*.exe)|*.exe", CheckFileExists = true })
                    if (dialog.ShowDialog(this) == DialogResult.OK) editor.Text = dialog.FileName;
            };
            save.Click += (sender, args) => { try { SaveSettings(); SetStatus(paths.HasIdentity ? "设置已保存" : "已保存，等待配对"); AppendLog("设置已保存。"); } catch (Exception error) { ReportError(error); } };
            doctor.Click += async (sender, args) => await RunOperation("doctor");
            pair.Click += async (sender, args) => await RunOperation("pair");
            unpair.Click += async (sender, args) => {
                if (MessageBox.Show(this, "解除当前设备绑定？本地工程文件会保留。", Text, MessageBoxButtons.OKCancel, MessageBoxIcon.Question) == DialogResult.OK) await RunOperation("unpair");
            };
            start.Click += async (sender, args) => await RunOperation("run");
            stop.Click += async (sender, args) => await StopWorker();
            open.Click += (sender, args) => {
                try { Directory.CreateDirectory(paths.Workspace); Process.Start(new ProcessStartInfo("explorer.exe", CommandLine.Quote(paths.Workspace)) { UseShellExecute = false }); }
                catch (Exception error) { ReportError(error); }
            };
            if (File.Exists(paths.Config)) {
                try {
                    var loaded = ConnectorSettings.Load(paths);
                    server.Text = loaded.serverUrl; editor.Text = loaded.executable; legacyDevelopment = loaded.development;
                    SetStatus(paths.HasIdentity ? "已配对，等待启动" : "等待配对");
                } catch { AppendLog("配置读取失败，请重新填写连接设置。"); }
            } else { AppendLog("欢迎使用。请先填写服务地址并选择嘉立创 EDA 专业版。"); }
            RefreshControls();
            Shown += async (sender, args) => { if (autoStart && paths.HasIdentity && File.Exists(paths.Config)) await RunOperation("run"); };
            FormClosing += (sender, args) => {
                if (allowClose) return;
                if (args.CloseReason != CloseReason.UserClosing) { worker.CloseInput(); return; }
                if (!autoStart) return;
                args.Cancel = true; Hide();
                tray.ShowBalloonTip(2500, Text, "应用保留在系统托盘，可在托盘菜单中停止或退出。", ToolTipIcon.Info);
            };
        }
        private static void AddLabel(TableLayoutPanel fields, string text, int row) {
            fields.Controls.Add(new Label { Text = text, Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleLeft, Margin = new Padding(0, 0, 6, 0) }, 0, row);
        }
        private static void ConfigureButton(Button button, string text) {
            button.Text = text; button.Width = 100; button.Height = 32; button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderColor = Color.FromArgb(210, 219, 232); button.BackColor = Color.White; button.Margin = new Padding(0, 0, 10, 0);
        }
        private void OnUi(Action action) {
            if (IsDisposed || Disposing) return;
            if (InvokeRequired) { try { BeginInvoke(action); } catch (InvalidOperationException) {} } else action();
        }
        private void AppendLog(string message) {
            lines.Enqueue(DateTime.Now.ToString("HH:mm:ss") + "  " + message);
            while (lines.Count > 150) lines.Dequeue();
            logs.Lines = lines.ToArray(); logs.SelectionStart = logs.TextLength; logs.ScrollToCaret();
        }
        private void SetStatus(string text) {
            status.Text = text;
            if (tray != null) { var label = "Nexus 工程连接器 · " + text; tray.Text = label.Length > 63 ? label.Substring(0, 63) : label; }
        }
        private void RefreshControls() {
            var active = busy || worker.IsRunning || stopping;
            foreach (var control in new Control[] { server, editor, pairing, browse, save, doctor, pair, start, unpair }) control.Enabled = !active && !exiting;
            unpair.Enabled = !active && !exiting && paths.HasIdentity;
            stop.Enabled = worker.IsRunning && activeCommand == "run" && !stopping;
            if (trayStart != null) { trayStart.Enabled = !active && !exiting; trayStop.Enabled = stop.Enabled; }
        }
        private void SaveSettings() {
            var settings = new ConnectorSettings { serverUrl = server.Text, executable = editor.Text.Trim(), workspaceRoot = paths.Workspace, development = legacyDevelopment };
            settings.Save(paths); server.Text = settings.serverUrl;
        }
        private void ReportError(Exception error) {
            var message = error is ArgumentException || error is FileNotFoundException ? error.Message : "操作未完成，请检查连接设置、权限与运行记录。";
            AppendLog(message); SetStatus("需要检查");
            MessageBox.Show(this, message, Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        private async Task RunOperation(string command) {
            if (worker.IsRunning || busy || exiting || stopping) return;
            try {
                SaveSettings();
                var code = command == "pair" ? pairing.Text.Trim() : null;
                if (command == "pair" && (string.IsNullOrWhiteSpace(code) || code.Length > 128 || code.Contains("\n") || code.Contains("\r"))) throw new ArgumentException("请输入 Nexus 工程设备页生成的一次性配对码。");
                if (command == "run" && !paths.HasIdentity) throw new ArgumentException("请先在 Nexus 工程设备页生成配对码，并完成设备配对。");
                busy = true; activeCommand = command;
                SetStatus(command == "run" ? "正在启动…" : command == "doctor" ? "正在检查 EDA…" : command == "pair" ? "正在配对…" : "正在解绑…");
                AppendLog(command == "run" ? "正在启动连接器。" : command == "doctor" ? "正在检查本机 EDA。" : command == "pair" ? "正在兑换一次性配对码。" : "正在解除设备绑定。");
                var pending = worker.RunAsync(command, code);
                if (command == "pair") pairing.Clear();
                if (command == "run") SetStatus("连接器运行中");
                RefreshControls();
                var result = await pending;
                if (result.Forced) { SetStatus("已停止，需核对任务"); AppendLog("正常停止超时，已终止本次工作进程。工程文件保留，请在 Nexus 核对任务。"); }
                else if (result.ExitCode != 0) { SetStatus("操作未完成"); AppendLog(command == "pair" ? "请按上方接口诊断处理；配对码过期时在网页重新生成。" : "请按上方具体诊断处理后重试。"); }
                else if (command == "pair") { SetStatus("配对成功"); AppendLog("设备配对成功，可以启动连接器。"); }
                else if (command == "unpair") SetStatus("设备已解绑");
                else if (command == "doctor") {
                    try {
                        var diagnostic = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(result.Output);
                        SetStatus(diagnostic.ContainsKey("connected") && diagnostic["connected"] is bool && (bool)diagnostic["connected"] ? "EDA 连接检查通过" : "EDA 尚未连接");
                    } catch { SetStatus("诊断结果无效"); }
                } else SetStatus("连接器已停止");
            } catch (Exception error) { ReportError(error); }
            finally { busy = false; activeCommand = null; RefreshControls(); }
        }
        private Task StopWorker() {
            if (stopTask != null && !stopTask.IsCompleted) return stopTask;
            if (!worker.IsRunning) return Task.FromResult(0);
            stopTask = StopWorkerCore();
            return stopTask;
        }
        private async Task StopWorkerCore() {
            stopping = true; SetStatus("正在停止…"); RefreshControls();
            try { await worker.StopAsync(); } catch (Exception error) { ReportError(error); }
            finally { stopping = false; RefreshControls(); }
        }
        private void RestoreWindow() { Show(); WindowState = FormWindowState.Normal; Activate(); }
        private async Task ExitApplication() {
            if (exiting) return;
            exiting = true; RefreshControls();
            await StopWorker(); allowClose = true; tray.Visible = false; Close();
        }
        protected override void Dispose(bool disposing) {
            if (disposing) { worker.Dispose(); if (tray != null) tray.Dispose(); }
            base.Dispose(disposing);
        }
    }
}
