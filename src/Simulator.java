// License: GPL. For details, see Readme.txt file.

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.openstreetmap.gui.jmapviewer.*;
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.*;
import org.openstreetmap.gui.jmapviewer.tilesources.BingAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import static java.lang.Math.*;

/**
 * Demonstrates the usage of {@link JMapViewer}
 *
 * @author Jan Peter Stotz
 */
public class Simulator extends JFrame implements JMapViewerEventListener {

    private static final long serialVersionUID = 1L;

    private JMapViewerTree treeMap;

    private JLabel zoomLabel;
    private JLabel zoomValue;

    private JLabel mperpLabelName;
    private JLabel mperpLabelValue;

    /**
     * Constructs the {@code Simulator}.
     */
    public Simulator() throws IOException {

        super("Geofence Simulator");
        init();

        AddMouseListeners();
    }

    private void AddMouseListeners() {
        map().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    map().getAttribution().handleAttribution(e.getPoint(), true);
                }
            }
        });

        map().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                boolean cursorHand = map().getAttribution().handleAttributionCursor(p);
                if (cursorHand) {
                    map().setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    map().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });
    }

    private double GetDistanceToClosestFence(Coordinate curLoc, ArrayList<MapMarker> fenceLocs){
        ArrayList<Double> distances = new ArrayList<>();
        for (MapMarker fence: fenceLocs) {
            distances.add(getDistance(curLoc, fence.getCoordinate()));
        }
        return Collections.min(distances);
    }

    private DataSet GetGPSData(String filePath) {

        System.out.println("Attempting to read in data...");

        DataSet ds = null;
        try {
            ds = new DataSet(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Read data in correctly.");

        return ds;
    }

    private void GetRoutedDistance(Coordinate start, Coordinate end){
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            // specify the host, protocol, and port
            HttpHost target = new HttpHost("weather.yahooapis.com", 80, "http");

            // specify the get request
            HttpGet getRequest = new HttpGet("http://dev.virtualearth.net/REST/V1/Routes/Driving" +
                    "?wp.0=" + start.getLat() + "," + start.getLon() +
                    "&wp.1=" + end.getLat() + "," + end.getLon() +
                    "&ra=routeSummariesOnly" +
                    "&key=Ah2BJh4cdLWewXKf-u5I98pNrwtZz6JJxfCnbC-5M4GTBeHKDbQdzxtOP8yypEmU");

            System.out.println("executing request to " + target);

            HttpResponse httpResponse = httpclient.execute(getRequest);
            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {
                System.out.println(EntityUtils.toString(entity));
            }else{
                JOptionPane.showMessageDialog(null, "Something went wrong, could not request route information",
                        "InfoBox: " + "Uh Oh.", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }
    }

    private void init() {
        setSize(400, 400);

        treeMap = new JMapViewerTree("Zones");

        // Listen to the map viewer for user operations so components will
        // receive events and update
        map().addJMVListener(this);

        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        JPanel panel = new JPanel(new BorderLayout());
        JPanel panelTop = new JPanel();
        JPanel panelBottom = new JPanel();
        JPanel helpPanel = new JPanel();

        mperpLabelName = new JLabel("Meters/Pixels: ");
        mperpLabelValue = new JLabel(String.format("%s", map().getMeterPerPixel()));

        zoomLabel = new JLabel("Zoom: ");
        zoomValue = new JLabel(String.format("%s", map().getZoom()));

        add(panel, BorderLayout.NORTH);
        add(helpPanel, BorderLayout.SOUTH);
        panel.add(panelTop, BorderLayout.NORTH);
        panel.add(panelBottom, BorderLayout.SOUTH);
        JLabel helpLabel = new JLabel("Use right mouse button to move,\n "
                + "left double click or mouse wheel to zoom.");
        helpPanel.add(helpLabel);
        JButton button = new JButton("Fit GPS Markers");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setDisplayToFitMapMarkers();
            }
        });
        panelBottom.add(button);

        JButton openButton = new JButton("Open GPS File");
        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                JFileChooser openFileDlg = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter("TEXT FILES", "txt", "text");
                openFileDlg.setFileFilter(filter);

                if (openFileDlg.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    File file = openFileDlg.getSelectedFile();
                    //This is where a real application would open the file.
                    System.out.println("Opening: " + file.getAbsolutePath() + ".");
                    plotPoints(file.getAbsolutePath());
                } else {
                    System.out.println("Open command cancelled by user.");
                }

            }
        });
        panelBottom.add(openButton);
        
        final JCheckBox showMapMarker = new JCheckBox("Map markers visible");
        showMapMarker.setSelected(map().getMapMarkersVisible());
        showMapMarker.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setMapMarkerVisible(showMapMarker.isSelected());
            }
        });
        panelBottom.add(showMapMarker);


        panelTop.add(zoomLabel);
        panelTop.add(zoomValue);
        panelTop.add(mperpLabelName);
        panelTop.add(mperpLabelValue);

        add(treeMap, BorderLayout.CENTER);
    }

    /**
     * @param miles Distance in miles to be converted to a double representing OSM radius distance.
     */
    private double convertMilesToOSM(double miles){
        return miles/69;
    }

    private JMapViewer map() {
        return treeMap.getViewer();
    }

    private static Coordinate c(double lat, double lon) {
        return new Coordinate(lat, lon);
    }

    /**
     * @param args Main program arguments
     */
    public static void main(String[] args) throws IOException {
        new Simulator().setVisible(true);
    }

    /*
     * Calculate distance between two points in latitude and longitude taking
     * into account height difference. If you are not interested in height
     * difference pass 0.0. Uses Haversine method as its base.
     *
     * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    private double distance(double lat1, double lat2, double lon1,
                                  double lon2, double el1, double el2) {

        final int R = 6371; // Radius of the earth

        Double latDistance = toRadians(lat2 - lat1);
        Double lonDistance = toRadians(lon2 - lon1);
        Double a = sin(latDistance / 2) * sin(latDistance / 2)
                + cos(toRadians(lat1)) * cos(toRadians(lat2))
                * sin(lonDistance / 2) * sin(lonDistance / 2);
        Double c = 2 * atan2(sqrt(a), sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = pow(distance, 2) + pow(height, 2);

        return sqrt(distance);
    }

    /*
     * Wrapper for the Haversine method.
     *
     * coord1, coord2, Points to get the distance between
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    private double getDistance(Coordinate coord1, Coordinate coord2){
        return distance(coord1.getLat(), coord2.getLat(), coord1.getLon(),coord2.getLon(), 0, 0);
    }

    private void plotPoints(String filePath){
        DataSet ds = GetGPSData(filePath);

        Layer personOne = new Layer("Person One");
        if(ds.fences.size() > 0){
            for (Fence fence:ds.fences) {
                map().addMapMarker(new MapMarkerCircle(personOne, new Coordinate(fence.location.getLat(),
                        fence.location.getLon()), convertMilesToOSM(fence.radius)));
            }
        }

        ArrayList<MapMarker> fenceLocations = new ArrayList<>(map().getMapMarkerList());

        for (Coordinate loc: ds.data) {
            if(GetDistanceToClosestFence(loc,fenceLocations) < 170){
                break;
            }
            MapMarkerDot newDot = new MapMarkerDot(personOne, null, loc.getLat(), loc.getLon());
            map().addMapMarker(newDot);
        }
    }

    private void updateZoomParameters() {
        if (mperpLabelValue != null)
            mperpLabelValue.setText(String.format("%s", map().getMeterPerPixel()));
        if (zoomValue != null)
            zoomValue.setText(String.format("%s", map().getZoom()));
    }

    @Override
    public void processCommand(JMVCommandEvent command) {
        if (command.getCommand().equals(JMVCommandEvent.COMMAND.ZOOM) ||
                command.getCommand().equals(JMVCommandEvent.COMMAND.MOVE)) {
            updateZoomParameters();
        }
    }
}
