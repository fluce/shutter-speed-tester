using LinqStatistics;
using PropertyChanged;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shapes;

namespace ShutterSpeedTester
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
        }

        ShutterSpeedLib.ShutterSpeedTester tester;

        private void Grid_Loaded(object sender, RoutedEventArgs e)
        {
            DataContext = ViewModel;

            Task.Factory.StartNew(() =>
            {
                while (true)
                    try
                    {
                        using (var tester = ShutterSpeedLib.ShutterSpeedTester.ProbeAndFind(s => Dispatcher.Invoke(()=>Log(s))))
                        {
                            tester.Log += (s, ev) => Dispatcher.Invoke(() => Log( ev.Message ));
                            tester.TimingMessageReceived += (s, ev) => Dispatcher.Invoke(()=> {
                                ViewModel.TimingCollection.Add(ev.Timing);
                                ViewModel.LastTiming = ev.Timing;
                                timingListBox.ScrollIntoView(ViewModel.TimingCollection.Last());
                            });
                            tester.Start();
                            while (tester.CurrentMode != 2)
                            {
                                tester.SetMode(2);
                                Thread.Sleep(1000);
                            }
                            tester.WaitEndOfReaderTask();
                        }
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"Global Exception {ex.GetType().Name} : {ex.Message}");
                    }

                
            });
        }

        public void Log(string s)
        {
            this.textBox.AppendText(s + "\n");
            var oldFocusedElement = FocusManager.GetFocusedElement(this);

            this.textBox.Focus();
            this.textBox.CaretIndex = this.textBox.Text.Length;
            this.textBox.ScrollToEnd();

            FocusManager.SetFocusedElement(this, oldFocusedElement);
        }

        public GlobalViewModel ViewModel { get; } = new GlobalViewModel();

        private void button_Click(object sender, RoutedEventArgs e)
        {
            ViewModel.TimingCollection.Clear();
        }
    }

    [ImplementPropertyChanged]
    public class GlobalViewModel
    {
        public ObservableCollection<TimeSpan> TimingCollection { get; } = new ObservableCollection<TimeSpan>();

        public GlobalViewModel()
        {
            TimingCollection.CollectionChanged += TimingCollection_CollectionChanged;
        }

        private void TimingCollection_CollectionChanged(object sender, System.Collections.Specialized.NotifyCollectionChangedEventArgs e)
        {
            if (TimingCollection.Count > 0)
                TimingMean = TimingCollection.Average(x => x.TotalMilliseconds);
            else
                TimingMean = double.NaN;

            if (TimingCollection.Count>1)
                TimingStdDev = TimingCollection.StandardDeviationP(x=>x.TotalMilliseconds);
            else
                TimingStdDev = double.NaN;
        }

        public double TimingMean { get; private set; }
        public double TimingStdDev { get; private set; }

        public TimeSpan LastTiming { get; set; }

        public string LastTimingAsShutterSpeed
        {
            get
            {
                var ms = LastTiming.TotalMilliseconds;
                if (ms < 1000)
                    return $"1/{(1000.0 / ms).ToString("0.00")} s";
                return $"{(ms / 1000.0).ToString("0.0")} s";

            }
        }

    }

}
