/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.grutta.autoportada;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

/**
 *
 * @author kosmos
 */
public class AutoPortada extends JPanel implements ActionListener, PropertyChangeListener {

    private static final String RESOURCES_PATH = System.getProperty("user.dir") + File.separator + "resources" + File.separator;
    private static final String TEMP_PATH = System.getProperty("user.dir") + File.separator + "temp" + File.separator;
    private static final String OUTPUT_PATH = System.getProperty("user.dir") + File.separator + "output" + File.separator;
    private static String INPUT_PATH = "";

    private JProgressBar progressBar;
    private JButton startButton;
    private JTextArea taskOutput;
    private JFileChooser fileChooser;
    private Task task;

    class Task extends SwingWorker<Void, Void> {

        /*
         * Main task. Executed in background thread.
         */
        @Override
        public Void doInBackground() {
            String log = generarPortadas(fileChooser.getSelectedFile());
            if (log != null) {
                JOptionPane.showMessageDialog(AutoPortada.this, log, "ERROR", JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }

        /*
         * Executed in event dispatching thread
         */
        @Override
        public void done() {
            Toolkit.getDefaultToolkit().beep();
            startButton.setEnabled(true);
            setCursor(null); //turn off the wait cursor
            taskOutput.append("¡Finalizado!\n");
            JOptionPane.showMessageDialog(AutoPortada.this,"Portadas generadas con éxito", "INFO", JOptionPane.INFORMATION_MESSAGE);
        }

        private String generarPortadas(File file) {
            createDirectories();
            
            System.out.println("RESOURCES_PATH: " + RESOURCES_PATH + "\tOK");
            System.out.println("TEMP_PATH: " + TEMP_PATH + "\t\tOK");
            System.out.println("OUTPUT_PATH: " + OUTPUT_PATH + "\tOK");
            System.out.println("INPUT_PATH: " + INPUT_PATH + "\t\t\tOK");

            String log = null;

            try {
                BufferedReader br = new BufferedReader(new FileReader(file));

                String sCurrentLine;
                sCurrentLine = br.readLine(); // Lee la primer linea que contiene las cabeceras

                String[] header = {"nombre_archivo", "id_comunidad", "titulo", "anio", "tipo", "nombre_evento", "institucion", "autores", "directores", "citacion", "handle"};
                String[] data = sCurrentLine.split(";");

                if (header.length != data.length) {
                    log = "Cabecera incorrecta. Debe seguir la forma: nombre_archivo;id_comunidad;titulo;anio;tipo;nombre_evento;institucion;autores;directores;citacion;handle";
                    System.out.println("Error: " + log);
                }

                for (int i = 0; i < header.length; i++) {
                    if (!data[i].equalsIgnoreCase(header[i])) {
                        log = "Cabecera incorrecta. Debe seguir la forma: nombre_archivo;id_comunidad;titulo;anio;tipo;nombre_evento;institucion;autores;directores;citacion;handle";
                        System.out.println("Error: " + log);
                    }
                }

                List<String> lines = new ArrayList<>();
                while ((sCurrentLine = br.readLine()) != null) {
                    lines.add(sCurrentLine);
                }

                //Initialize progress property.
                setProgress(0);
                for (int i = 0; i < lines.size(); i++) {
                    data = lines.get(i).split(";");
                    if (data.length == 11) {
                        System.out.println("Registro " + i + ": " + data[0]);
                        log = armarPortada(header, data);
                        if (log == null) {
                            log = unirPDF(data[0]);
                        }
                        // Suma a la barra de progreso
                        setProgress((i + 1) * 100 / lines.size());
                    } else {
                        log = "Faltan o sobran campos en el registro " + i;
                        System.out.println("Error: " + log);
                    }
                }
            } catch (IOException e) {
                log = e.getMessage();
                System.out.println("Error: " + log);
            }
            return log;
        }

        public String armarPortada(String[] header, String[] data) {
            String log = null;

            JasperReport jasperReport;
            JasperPrint jasperPrint;
            JRDataSource datasource;
            List<Map<String, Object>> dataList = new ArrayList<>();
            Map<String, Object> map = new HashMap<>();
            Map<String, Object> parameters = new HashMap<>();

            try {
                InputStream logo_riunne = new FileInputStream(new File(RESOURCES_PATH + "logo_riunne.png"));
                InputStream comunidad = new FileInputStream(new File(RESOURCES_PATH + data[1] + ".png"));
                InputStream logo_unne = new FileInputStream(new File(RESOURCES_PATH + "logo_unne.png"));
                InputStream cc = new FileInputStream(new File(RESOURCES_PATH + "cc.png"));
                parameters.put("logo_riunne", ImageIO.read(new ByteArrayInputStream(JRLoader.loadBytes(logo_riunne))));
                parameters.put("comunidad", ImageIO.read(new ByteArrayInputStream(JRLoader.loadBytes(comunidad))));
                parameters.put("logo_unne", ImageIO.read(new ByteArrayInputStream(JRLoader.loadBytes(logo_unne))));
                parameters.put("cc", ImageIO.read(new ByteArrayInputStream(JRLoader.loadBytes(cc))));
            } catch (IOException ex) {
                log = ex.getMessage();
                System.out.println("Error: " + log);
            } catch (JRException ex) {
                Logger.getLogger(AutoPortada.class.getName()).log(Level.SEVERE, null, ex);
            }

            for (int i = 0; i < header.length; i++) {
                if (header[i].equals("autores") || header[i].equals("directores")) {
                    data[i] = data[i].replace("||", "\n");
                    map.put(header[i], !data[i].equals("") ? data[i] : null);
                } else {
                    map.put(header[i], !data[i].equals("") ? data[i] : null);
                }
            }
            dataList.add(map);

            try {
                // If compiled file is not found, then compile XML template
                if (!new File(RESOURCES_PATH + "portada.jasper").exists()) {
                    JasperCompileManager.compileReportToFile(RESOURCES_PATH + "portada.jrxml", RESOURCES_PATH + "portada.jasper");
                }                
                jasperReport = (JasperReport) JRLoader.loadObjectFromFile(RESOURCES_PATH + "portada.jasper");
                System.out.println("Cargar jasperReport        OK");              
                datasource = new JRBeanCollectionDataSource(dataList);
                System.out.println("Cargando datasource        OK");                                
                jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, datasource);                
                System.out.println("jasperPrint     OK");                
                JasperExportManager.exportReportToPdfFile(jasperPrint, TEMP_PATH + data[0]);                
                System.out.println("exportReportToPdfFile       OK");
            } catch (JRException ex) {
                log = ex.getMessage();
                System.out.println("Error: " + log);
            }

            return log;
        }

        public String unirPDF(String fileName) {
            String log = null;

            try {
                //Instantiating PDFMergerUtility class
                PDFMergerUtility PDFmerger = new PDFMergerUtility();

                //Setting the destination file
                PDFmerger.setDestinationFileName(OUTPUT_PATH + fileName);

                //adding the source files
                PDFmerger.addSource(TEMP_PATH + fileName);
                PDFmerger.addSource(INPUT_PATH + fileName);

                //Merging the two documents
                PDFmerger.mergeDocuments();
            } catch (IOException ex) {
                log = ex.getMessage();
                System.out.println("Error: " + log);
            }

            return log;
        }

        private void createDirectories() {
            File temp = new File(TEMP_PATH);
            if (!temp.exists()) {
                temp.mkdir();
            }
            File output = new File(OUTPUT_PATH);
            if (!output.exists()) {
                output.mkdir();
            }
        }
    }

    public AutoPortada() {
        super(new BorderLayout());

        //Create the demo's UI.
        fileChooser = new JFileChooser();

        startButton = new JButton("Abrir");
        startButton.setActionCommand("abrir");
        startButton.addActionListener(this);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        taskOutput = new JTextArea(20, 20);
        taskOutput.setMargin(new Insets(5, 5, 5, 5));
        taskOutput.setEditable(false);

        JPanel panel = new JPanel();
        panel.add(startButton);
        panel.add(progressBar);

        add(panel, BorderLayout.PAGE_START);
        add(new JScrollPane(taskOutput), BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    }

    /**
     * Invoked when the user presses the start button.
     */
    public void actionPerformed(ActionEvent evt) {
        openFile();
    }

    /**
     * Invoked when task's progress property changes.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress" == evt.getPropertyName()) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);
            taskOutput.append(String.format("Completado el %d%% del trabajo.\n", task.getProgress()));
        }
    }

    /**
     * Create the GUI and show it. As with all GUI code, this must run on the
     * event-dispatching thread.
     */
    private static void createAndShowGUI() {
        //Create and set up the window.
        JFrame frame = new JFrame("Generar portadas");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        JComponent newContentPane = new AutoPortada();
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);

        //Display the window.
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        /* Set the look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("GTK+".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(AutoPortada.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }

    private void openFile() {
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Planillas", "csv");
        fileChooser.setFileFilter(filter);

        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            if (fileChooser.getSelectedFile().getPath().endsWith(".csv")) {
                System.out.println("Archivo: " + fileChooser.getSelectedFile().getPath() + "\tAbierto!");
                INPUT_PATH = fileChooser.getSelectedFile().getParent() + File.separator;
                /// Start task ///
                startButton.setEnabled(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                //Instances of javax.swing.SwingWorker are not reusuable, so
                //we create new instances as needed.
                task = new Task();
                task.addPropertyChangeListener(this);
                task.execute();
                /// --- ///

//                String log = generarPortadas(fileChooser.getSelectedFile());
//                if (log == null) {
//                    JOptionPane.showMessageDialog(this, "Portadas generadas con éxito", "INFO", JOptionPane.INFORMATION_MESSAGE);
//                } else {
//                    JOptionPane.showMessageDialog(this, log, "ERROR", JOptionPane.ERROR_MESSAGE);
//                }
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo abrir el archivo", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.out.println("Error");
            }
        } else {
            System.out.println("Open command cancelled by user.");
        }
    }
}