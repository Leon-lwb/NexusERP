using System;
using System.IO;
using System.Threading;
using System.Security.Principal;
using System.Windows.Forms;

namespace Nexus.EngineeringConnector {
    /// <summary>原生桌面入口，使用当前用户会话单实例，不要求管理员权限。</summary>
    internal static class Program {
        /// <summary>以能否取得所有权判断运行状态，重复实例先释放句柄再显示提示。</summary>
        internal static Mutex AcquireInstance(string name) {
            var mutex = new Mutex(false, name);
            bool acquired;
            try { acquired = mutex.WaitOne(0); }
            catch (AbandonedMutexException) { acquired = true; }
            if (acquired) return mutex;
            mutex.Dispose(); return null;
        }
        [STAThread]
        private static int Main(string[] args) {
            Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
            if (args.Length == 2 && args[0] == "--self-test") {
                var code = SelfTests.Run(args[1], AppDomain.CurrentDomain.BaseDirectory).GetAwaiter().GetResult();
                try { SelfTests.RenderPreview(args[1], AppDomain.CurrentDomain.BaseDirectory); }
                catch (Exception error) {
                    File.WriteAllText(args[1], new System.Web.Script.Serialization.JavaScriptSerializer().Serialize(new { checks = new string[0], failed = error.Message })); return 1;
                }
                return code;
            }
            var identity = WindowsIdentity.GetCurrent().User.Value;
            using (var mutex = AcquireInstance("Local\\Nexus.EngineeringConnector." + identity)) {
                if (mutex == null) { MessageBox.Show("连接器已运行，请从系统托盘打开窗口。", "Nexus 工程连接器", MessageBoxButtons.OK, MessageBoxIcon.Information); return 0; }
                try { Application.Run(new MainForm(ConnectorPaths.Current(), true)); return 0; }
                catch { MessageBox.Show("应用启动失败，请确认已完整解压分发包且当前用户目录可写。", "Nexus 工程连接器", MessageBoxButtons.OK, MessageBoxIcon.Error); return 1; }
                finally { mutex.ReleaseMutex(); }
            }
        }
    }
}
