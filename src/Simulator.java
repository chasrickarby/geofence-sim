// License: GPL. For details, see Readme.txt file.

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.apache.commons.io.IOUtils;

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
    private DataSet dataset;
    private Layer personOne = new Layer("Person One");
    private ArrayList<MapMarker> fenceLocations;
    private JLabel helpLabel;



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

        try {
            dataset = new DataSet(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Read data in correctly.");

        return dataset;
    }

    private int GetRoutedTravelTime(Coordinate start, Coordinate end){
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            // specify the get request
            HttpGet getRequest = new HttpGet("http://dev.virtualearth.net/REST/V1/Routes/Driving" +
                    "?wp.0=" + start.getLat() + "," + start.getLon() +
                    "&wp.1=" + end.getLat() + "," + end.getLon() +
                    "&ra=routeSummariesOnly" +
                    "&key=Ah2BJh4cdLWewXKf-u5I98pNrwtZz6JJxfCnbC-5M4GTBeHKDbQdzxtOP8yypEmU");

            HttpResponse httpResponse = httpclient.execute(getRequest);
            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {
                return GetTravelTime(entity);
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
        return 0;
    }

    /*
    Based on a REST response gets the travel time (using routing) between two points, in seconds.
     */
    private int GetTravelTime(HttpEntity response) throws IOException {
        StringWriter writer=new StringWriter();
        IOUtils.copy(response.getContent(),writer);
        String responseStr = writer.toString();
        Pattern p = Pattern.compile("\"travelDuration\":[0-9]+,");
        Matcher m = p.matcher(responseStr);
        m.find();
        String requiredData = m.group();

        p = Pattern.compile("[0-9]+");
        m = p.matcher(requiredData);
        m.find();
        int n = Integer.parseInt(m.group());
        return n;
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
        helpLabel = new JLabel("Use right mouse button to move,\n "
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
                    plotFences(file.getAbsolutePath());
                } else {
                    System.out.println("Open command cancelled by user.");
                }
                plotAPoint();
            }
        });
        panelBottom.add(openButton);

        JButton plotNextPointButton = new JButton("Plot Next Point");
        plotNextPointButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                plotAPoint();
            }
        });
        panelBottom.add(plotNextPointButton);

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

    private void plotFences(String filePath){
        DataSet ds = GetGPSData(filePath);

        personOne = new Layer("Person One");
        Random random = new Random(System.currentTimeMillis());

        fenceLocations = new ArrayList<>(map().getMapMarkerList());

        for (int i = 0; i < ds.data.size()*.001; i++) {
            fenceLocations = new ArrayList<>(map().getMapMarkerList());
            int index = random.nextInt(ds.data.size());

            // Make sure none of the fences overlap
            if(fenceLocations.size() > 0){
                while (GetDistanceToClosestFence(ds.data.get(index), fenceLocations) < 170){
                    index = random.nextInt(ds.data.size());
                }
            }

            map().addMapMarker(new MapMarkerCircle(personOne, new Coordinate(ds.data.get(index).getLat(),
                    ds.data.get(index).getLon()), convertMilesToOSM(0.1)));
        }
    }

    private void plotAPoint(){
        Coordinate loc = dataset.data.get(0);
        if(GetDistanceToClosestFence(loc,fenceLocations) < 170){
            MapMarkerDot newDot = new MapMarkerDot(Color.GREEN, loc.getLat(), loc.getLon());
            map().addMapMarker(newDot);
            helpLabel.setText("Geofence Hit: " + loc.time );
        }else{
            MapMarkerDot newDot = new MapMarkerDot(personOne, null, loc.getLat(), loc.getLon());
            map().addMapMarker(newDot);
            helpLabel.setText("Current Time: " + loc.time );
        }
        dataset.data.remove(0);
    }

    private Coordinate GetClosestFenceCoordinate(Coordinate coordinate, ArrayList<MapMarker> fenceLocs) {
        Coordinate closestFence = fenceLocs.get(0).getCoordinate();
        for (MapMarker fence: fenceLocs) {
            if(getDistance(coordinate, fence.getCoordinate()) < getDistance(coordinate, closestFence)){
                closestFence = fence.getCoordinate();
            }
        }

        return new Coordinate(closestFence.getLat(),closestFence.getLon());
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
