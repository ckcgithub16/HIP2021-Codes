/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;

/**
 *
 * @author lee
 */
public class LearnXChart extends JFrame {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        LearnXChart lxc = new LearnXChart();
        lxc.addDataLoop();
    }
 
    XYChart xyChart;
    final XChartPanel<XYChart> xcpXYC;
    
    public LearnXChart() {
        // double[] xData = new double[] { 0.0, 1.0, 2.0 };
        // double[] yData = new double[] { 2.0, 1.0, 0.0 };
        
        // getContentPane().setSize(600, 400);
        JPanel jp = new JPanel();
        getContentPane().add(jp);
        JPanel jpSub = new JPanel();
        jpSub.setSize(600, 400);
        jp.add(jpSub);

        xyChart = new XYChart(jpSub.getWidth(), jpSub.getHeight());
        xyChart.addSeries("Test", new double[]{0.0}, new double[]{0.0});
        xcpXYC = new XChartPanel<>(xyChart);
        jpSub.add(xcpXYC);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        pack();
        setVisible(true);

        aldXData = new ArrayList();
        aldYData = new ArrayList();
        lStartTime = System.currentTimeMillis();
    }
    
    ArrayList<Double> aldXData;
    ArrayList<Double> aldYData;
    long lStartTime;
    
    void addDataLoop() {
        while(true) {
            double dx = (System.currentTimeMillis() - lStartTime) / 1000.0;
            aldXData.add(dx);
            while(aldXData.size() > 400) {
                aldXData.remove(0);
            }
            aldYData.add(dx);
            while(aldYData.size() > 400) {
                aldYData.remove(0);
            }
            SwingUtilities.invokeLater(() -> {
                    xyChart.updateXYSeries("Test", aldXData, aldYData, null);
                    xcpXYC.repaint();
                }
            );
            // xyChart
            // xcpXYC.repaint();
            
            try {
                Thread.sleep(100);
            }
            catch(InterruptedException ie) {
            }
            
        }
    }
}
