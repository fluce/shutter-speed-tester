using System;
using System.Collections.Generic;
using System.IO.Ports;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ShutterSpeedLib
{
    public class ShutterSpeedTester : IDisposable
    {
        SerialPort port;
        public ShutterSpeedTester(string comPort)
        {
            port = new SerialPort(comPort, 115200, Parity.None, 8, StopBits.One);

            port.ReadTimeout = 500;
            port.NewLine = "\r\n";
            port.DtrEnable = true;
            port.Handshake = Handshake.RequestToSend;
        }

        public bool TryHandshake()
        {
            try
            {
                port.Open();
                port.WriteLine("HELLO");
                return port.WaitFor(InternalLog, x => x.StartsWith("SHTR10", StringComparison.Ordinal), TimeSpan.FromSeconds(50));
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        public void Start()
        {
            LaunchReaderTask();
        }

        public void WaitEndOfReaderTask()
        {
            readerTask.Wait();
        }

        public void SetMode(int mode)
        {
            InternalLog($"Setting mode {mode}");
            port.WriteLine(mode.ToString());
            //            return port.WaitFor(x => x.StartsWith($"SHTR10 MODE {mode}", StringComparison.Ordinal), TimeSpan.FromSeconds(5));
        }

        private CancellationTokenSource readerTs = new CancellationTokenSource();
        private Task readerTask;
        public void LaunchReaderTask()
        {
            readerTask = Task.Factory.StartNew(() =>
            {
                while (!readerTs.IsCancellationRequested)
                {
                    try
                    {
                        var i = port.BytesToRead;
                        if (i > 0)
                        {
                            string message = port.ReadLine();
                            InternalLog("Reader : " + message);
                            var splittedMessage = message.Split(' ');
                            var m = splittedMessage.First();
                            var p = splittedMessage.Skip(1).ToArray();
                            if (m == "SHTR10")
                                HandleMagic(p);
                            if (m == "M1")
                                HandleM1(p);
                            else if (m == "M2")
                                HandleM2(p);
                            else if (m == "M3")
                                HandleM3(p);
                        }
                    }
                    catch (TimeoutException) { }
                    catch (Exception e)
                    {
                        InternalLog($"Exception {e.GetType().Name} : {e.Message}");
                        if (!port.IsOpen)
                            HandleDeviceLost();
                    }
                }
            });

        }

        public int CurrentMode { get; set; }

        private void HandleMagic(string[] p)
        {
            if (p.Length == 2)
                if (p[0] == "MODE")
                {
                    CurrentMode = int.Parse(p[1]);
                    InternalLog($"Mode={p[1]}");
                }
        }

        private void HandleDeviceLost()
        {
            readerTs.Cancel();
        }

        public class LogEventArgs : EventArgs
        {
            public string Message { get; set; }
        }

        public event EventHandler<LogEventArgs> Log;

        private void InternalLog(string s)
        {
            if (Log != null)
                Log(this, new LogEventArgs { Message = s });
            else
                Console.WriteLine(s);
        }

        public class TimingMessageEventArgs : EventArgs
        {
            public TimeSpan Timing { get; set; }
        }

        public event EventHandler<TimingMessageEventArgs> TimingMessageReceived;

        private void HandleM1(string[] p)
        {

        }

        private void HandleM2(string[] p)
        {
            int l;
            CurrentMode = 2;
            if (p.Length == 1 && int.TryParse(p[0], out l) && l!=0)
            {
                InternalLog($"Msg2 {l} ms => 1 / {1000000f / l}");
                if (TimingMessageReceived != null)
                    TimingMessageReceived(this, new TimingMessageEventArgs { Timing = TimeSpan.FromTicks(l * TimeSpan.TicksPerMillisecond / 1000) });
            }
        }

        private void HandleM3(string[] p)
        {

        }

        public void Dispose()
        {
            if (readerTask != null && readerTask.Status == TaskStatus.Running)
            {
                readerTs.Cancel();
                readerTask.Wait();
                readerTask = null;
            }
            if (port != null)
                port.Dispose();
            port = null;
        }

        public static ShutterSpeedTester ProbeAndFind(Action<string> logger)
        {
            ShutterSpeedTester tester=null;
            foreach (var comPort in SerialPort.GetPortNames())
                try
                {
                    tester = new ShutterSpeedTester(comPort);
                    if (logger!=null)
                        tester.Log += (s,e) => logger(e.Message);
                    var hs = tester.TryHandshake();
                    tester.InternalLog($"{comPort} Handshake result : {hs}");
                    if (hs)
                    {
                        tester.Log = null;
                        return tester;
                    }
                    tester.Dispose();
                    Thread.Sleep(2000);
                }
                catch (Exception e)
                {
                    tester.InternalLog($"Probe Exception {e.GetType().Name} : {e.Message}");
                    tester.Dispose();
                }
            return null;
        }

        private static void Tester_Log(object sender, LogEventArgs e)
        {
            throw new NotImplementedException();
        }
    } 


    internal static class SerialPortExtension
    {
        public static bool WaitFor(this SerialPort port, Action<string> logger,  Func<string, bool> f, TimeSpan timeout)
        {
            logger("Waiting For started");
            var ts = new CancellationTokenSource(timeout);
            var t = Task<bool>.Factory.StartNew(() => {
                while (!ts.IsCancellationRequested)
                {
                    try
                    {
                        var s = port.ReadLine();
                        logger("WaitFor" + s);
                        var r = f(s);
                        if (r)
                            return true;
                    }
                    catch (TimeoutException)
                    {
                        logger("WaitFor TimeoutException");
                    }
                    catch (Exception)
                    {
                        logger("WaitFor Exception");
                        return false;
                    }
                }
                logger("Waiting For timedout");
                return false;
            });

            var r2 = t.Wait(timeout);
            if (r2)
                return t.Result;
            ts.Cancel();
            t.Wait();
            return false;
        }

    }

}
