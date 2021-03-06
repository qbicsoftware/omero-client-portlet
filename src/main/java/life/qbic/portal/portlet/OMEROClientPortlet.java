package life.qbic.portal.portlet;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.server.*;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.shared.ui.ValueChangeMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Grid.Column;
import com.vaadin.ui.Grid.SelectionMode;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Label;
import com.vaadin.ui.Layout;
import com.vaadin.ui.Link;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.components.grid.HeaderRow;
import com.vaadin.ui.renderers.ComponentRenderer;
import com.vaadin.ui.BrowserFrame;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import life.qbic.omero.BasicOMEROClient;
import life.qbic.portal.utils.ConfigurationManager;
import life.qbic.portal.utils.ConfigurationManagerFactory;
import loci.poi.util.SystemOutLogger;
import loci.poi.util.TempFile;
import omero.gateway.model.FileAnnotationData;
import omero.gateway.model.MapAnnotationData;
import omero.model.AnnotationAnnotationLink;
import omero.model.Format;
import omero.model.ImageI;
import omero.model.NamedValue;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point for the portlet omero-client-portlet.
 *
 */

@Theme("mytheme")
@SuppressWarnings("serial")
@Widgetset("life.qbic.portal.portlet.AppWidgetSet")

public class OMEROClientPortlet extends QBiCPortletUI {

    private static final Logger LOG = LogManager.getLogger(OMEROClientPortlet.class);

    ///////////////////////////////
    private final ConfigurationManager cm = ConfigurationManagerFactory.getInstance();

    private BasicOMEROClient omeroClient;
    private final List<Project> projects;

    private final List<Sample> samples;
    private final List<ImageInfo> imageInfos;
    private ComboBox<Project> projectBox;
    private Label projectLabel;
    private Button refreshButton;
    private Grid<Sample> sampleGrid;
    private Grid<ImageInfo> imageInfoGrid;

    public OMEROClientPortlet() {
        projects = new ArrayList<>();
        samples = new ArrayList<>();
        imageInfos = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Layout getPortletContent(final VaadinRequest request) {

        try {
            omeroClient = new BasicOMEROClient(cm.getOmeroUser(), cm.getOmeroPassword(), cm.getOmeroHostname(), Integer.parseInt(cm.getOmeroPort()));
        } catch (Exception e) {
            LOG.error("Unexpected exception during omero client creation.");
            LOG.debug(e);
            return new VerticalLayout();
        }

        Layout result;
        try {
            result = displayData();
        } catch (Exception e) {
            LOG.error(e.getMessage());
            LOG.debug(e);
            Notification.show(e.getMessage());
            result = new VerticalLayout();
        }

        return result;
    }

    private Layout displayData() {

        final VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSpacing(true);
        mainLayout.setMargin(true);
        mainLayout.setSizeFull();

        mainLayout.addComponent(this.getImgViewer());

        return mainLayout;
    }

    private Panel getImgViewer() {

        loadProjects();

        Panel imgViewerPanel = new Panel("Image Viewer");
        imgViewerPanel.setSizeFull();

        VerticalLayout panelContent = new VerticalLayout();
        panelContent.setSpacing(true);
        panelContent.setMargin(true);
        panelContent.setWidth("100%");
        panelContent.setHeight("100%");

        VerticalLayout projectLayout = new VerticalLayout();
        projectLayout.setSpacing(true);
        projectLayout.setMargin(false);
        projectLayout.setWidth("100%");
        projectLayout.setHeight("100%");

        HorizontalLayout topPanelLayout = new HorizontalLayout();
        topPanelLayout.setSpacing(true);
        topPanelLayout.setMargin(false);
        topPanelLayout.setWidth("50%");
        topPanelLayout.setHeight("100%");

        projectBox = new ComboBox<>("Select project:");
        projectBox.setEmptySelectionAllowed(false);
        projectBox.setWidth("100%");
        projectBox.setDataProvider(new ListDataProvider<>(projects));
        projectBox.setItemCaptionGenerator(Project::getName);

        refreshButton = new Button("Refresh");
        refreshButton.setWidth("100%");

        projectLabel = new Label("<br>", ContentMode.HTML);
        projectLabel.setWidth("100%");

        projectLayout.addComponent(projectBox);
        projectLayout.addComponent(refreshButton);
        projectLayout.setWidth("100%");

        topPanelLayout.addComponent(projectLayout);
        topPanelLayout.addComponent(projectLabel);

        panelContent.addComponent(topPanelLayout);

        // Have a horizontal split panel as its root layout
        GridLayout hsplit = new GridLayout(6, 2);
        hsplit.setSpacing(true);
        hsplit.setWidth("100%");
        hsplit.setHeight("600px");

        ///////////////////////
        // sample grid

        sampleGrid = new Grid<>();

        ListDataProvider<Sample> sampleListDataProvider = new ListDataProvider<>(samples);
        sampleGrid.setDataProvider(sampleListDataProvider);
        sampleGrid.setSelectionMode(SelectionMode.SINGLE);
        sampleGrid.setCaption("Samples");
        sampleGrid.setSizeFull();

        Column<Sample, String> sampleCodeColumn = sampleGrid.addColumn(Sample::getCode).setCaption("Code");
        Column<Sample, String> sampleNameColumn = sampleGrid.addColumn(Sample::getName).setCaption("Name");

        HeaderRow sampleFilterRow = sampleGrid.appendHeaderRow();

        setupColumnFilter(sampleListDataProvider, sampleCodeColumn, sampleFilterRow);
        setupColumnFilter(sampleListDataProvider, sampleNameColumn, sampleFilterRow);

        ///////////////////
        //image grid
        imageInfoGrid = new Grid<>();
        Column<ImageInfo, Component> imageThumbnailColumn = imageInfoGrid.addColumn(imageInfo -> {
            Resource thumbnailResource = new ExternalResource("data:image/jpeg;base64,"+ Base64.encodeBase64String(imageInfo.getThumbnail()));
            return (Component) new Image("",thumbnailResource);
        }).setCaption("Thumbnail");
        Column<ImageInfo, String> imageNameColumn = imageInfoGrid.addColumn(ImageInfo::getName).setCaption("Name");
        //Column<ImageInfo, String> imageSizeColumn = imageInfoGrid.addColumn(ImageInfo::getSize).setCaption("Size (X,Y,Z)");
        //Column<ImageInfo, String> imageTpsColumn = imageInfoGrid.addColumn(ImageInfo::getTimePoints).setCaption("Image Time Points");

        Column<ImageInfo, String> imageSizeColumn = imageInfoGrid.addColumn(imageInfo -> {
            // Exceptions need to be handled here since they are event based and do not bubble up
            try{
                return imageInfo.getSize() + " x " + imageInfo.getTimePoints();
            } catch (Exception e) {
                LOG.error("Could not generate image size for imageId: " + imageInfo.getImageId());
                LOG.debug(e);
                return "Not available";
            }
        }).setCaption("Spatio-temporal Size (X x Y x Z x T)");

        Column<ImageInfo, String> imageChannelsColumn = imageInfoGrid.addColumn(ImageInfo::getChannels).setCaption("Channels");
        Column<ImageInfo, Component> imageFullColumn = imageInfoGrid.addColumn(imageInfo -> {
            // Exceptions need to be handled here since they are event based and do not bubble up
            try{
                return (Component) imageLinkButton(imageInfo.getImageId()); //linkToFullImage(imageInfo.getImageId());
            } catch (Exception e) {
                LOG.error("Could not generate full image link for imageId: " + imageInfo.getImageId());
                LOG.debug(e);
                Label noFullImageLabel = new Label("Not available.");
                return (Component) noFullImageLabel;
            }
        }).setCaption("Full Image");
        Column<ImageInfo, Component> downloadImageColumn = imageInfoGrid.addColumn(imageInfo -> {
            // Exceptions need to be handled here since they are event based and do not bubble up
            try {
                Button downloadImageButton = downloadImage(imageInfo.getImageId());
                return (Component) downloadImageButton;
            } catch (Exception e) {
                LOG.error("Could not generate link for imageId: "+ imageInfo.getImageId());
                LOG.debug(e);
                Label noDownloadLabel = new Label("No download available.");
                return (Component) noDownloadLabel;
            }
        }).setCaption("Download Image");
        Column<ImageInfo, Component> imageMetadataColumn = imageInfoGrid.addColumn(imageInfo -> {
            // Exceptions need to be handled here since they are event based and do not bubble up
            try {
                return (Component) metadataButton(imageInfo.getImageId());
            }catch (Exception e) {
                LOG.error("Could not create metadata component for imageId: " + imageInfo.getImageId());
                LOG.debug(e);
                Label noMetadataLabel = new Label("");
                return (Component) noMetadataLabel;
            }}).setCaption("Metadata");

        imageThumbnailColumn.setRenderer(new ComponentRenderer());
        imageFullColumn.setRenderer(new ComponentRenderer());
        downloadImageColumn.setRenderer(new ComponentRenderer());
        imageMetadataColumn.setRenderer(new ComponentRenderer());


        ListDataProvider<ImageInfo> imageListProvider = new ListDataProvider<>(imageInfos);
        imageInfoGrid.setDataProvider(imageListProvider);
        imageInfoGrid.setCaption("Images");
        imageInfoGrid.setSelectionMode(SelectionMode.NONE);
        imageInfoGrid.setSizeFull();
        imageInfoGrid.setStyleName("gridwithpics100px");

        HeaderRow imageFilterRow = imageInfoGrid.appendHeaderRow();

        setupColumnFilter(imageListProvider, imageNameColumn, imageFilterRow);
        setupColumnFilter(imageListProvider, imageSizeColumn, imageFilterRow);
        //setupColumnFilter(imageListProvider, imageTpsColumn, imageFilterRow);
        setupColumnFilter(imageListProvider, imageChannelsColumn, imageFilterRow);

        /////////////////////////////////////

        hsplit.addComponent(sampleGrid, 0, 0, 1, 1);
        hsplit.addComponent(imageInfoGrid, 2,0, 5,1);

        panelContent.addComponent(hsplit);

        imgViewerPanel.setContent(panelContent);

        ////////////////////////////////////////////////////////////////////////
        registerListeners();

        return imgViewerPanel;
    }

    private void registerListeners() {
        // Project selection
        projectBox.addSelectionListener(event -> {
            if (event.getSelectedItem().isPresent()) {
                Project selectedProject = event.getSelectedItem().get();
                // update label
                projectLabel.setValue("<b>" + selectedProject.getName() + "</b><br>"
                    + selectedProject.getDescription());
                // clear unrelated samples
                imageInfos.clear();
                samples.clear();
                // load new samples
                HashMap<Long, HashMap<String, String>> projectSamples = omeroClient
                    .getDatasets(selectedProject.getId());
                projectSamples.forEach( (sampleId,sampleInfo) -> {
                    String sampleCode = sampleInfo.get("name");
                    String sampleName = sampleInfo.get("desc");
                    Sample sample = new Sample(sampleId, sampleName, sampleCode);
                    samples.add(sample);
                });
                refreshGrid(imageInfoGrid);
                refreshGrid(sampleGrid);

            } else {
                projectLabel.setValue("");
            }
        });

        sampleGrid.addSelectionListener(event -> {
            imageInfos.clear();
            if (event.getFirstSelectedItem().isPresent()) {
                Sample selectedSample = event.getFirstSelectedItem().get();
                HashMap<Long, String> sampleImageMap = omeroClient.getImages(selectedSample.getId());
                sampleImageMap.forEach( (imageId, ignoredImageName) -> {
                    HashMap<String, String> imageInformationMap = omeroClient.getImageInfo(selectedSample.getId(), imageId);

                    byte[] thumbnail = new byte[0];
                    String imageName = imageInformationMap.get("name");
                    String imageSize = imageInformationMap.get("size");
                    String imageTimePoints = imageInformationMap.get("tps");
                    String imageChannels = imageInformationMap.get("channels");
                    try {
                        ByteArrayInputStream thumbnailInputStream = omeroClient.getThumbnail(selectedSample.getId(), imageId);
                        thumbnail = new byte[thumbnailInputStream.available()];
                        // ignore integer and store in byte array
                        thumbnailInputStream.read(thumbnail);
                        thumbnailInputStream.close();
                    } catch (IOException ioException) {
                        LOG.error("Could not retrieve thumbnail for image:" + imageId);
                        LOG.debug(ioException);
                    }
                    ImageInfo imageInfo = new ImageInfo(imageId, imageName, thumbnail, imageSize, imageTimePoints, imageChannels);
                    imageInfos.add(imageInfo);
                });
            } else {
                //remove selected images
                imageInfos.clear();
            }
            refreshGrid(imageInfoGrid);
        });

        refreshButton.addClickListener(event -> {
            imageInfos.clear();
            samples.clear();
            projects.clear();
            projectBox.setSelectedItem(null);
            loadProjects();
            refreshGrid(imageInfoGrid);
            refreshGrid(sampleGrid);
            Notification.show("Refresh was performed.");
        });
    }

    /**
     * Loads projects from omero into {@link OMEROClientPortlet#projects}
     */
    private void loadProjects() {
        projects.clear();
        HashMap<Long, String> projectMap = omeroClient.loadProjects();

        for (Entry<Long, String> entry : projectMap.entrySet()) {
            Long projectId = entry.getKey();

            HashMap<String, String> projectInfo = omeroClient.getProjectInfo(projectId);

            Project project = new Project(projectId, projectInfo.get("name"), projectInfo.get("desc"));
            projects.add(project);
        }
    }

    /**
     *
     * @param imageId the image for which a link should be generated
     * @return a vaadin {@link Link} component
     */
    private Link linkToFullImage(long imageId) {
        String requestUrl = omeroClient.composeImageDetailAddress(imageId);
        Resource fullImage = new ExternalResource(requestUrl);
        Link fullImageLink = new Link("Open Image", fullImage);
        fullImageLink.setTargetName("_blank");
        return fullImageLink;
    }

    /**
     * Allows the user to download an image in ome.tiff format for a given image id
     *
     * @param imageId the image for which a download link should be generated
     * @return a vaadin {@link Button} component linking to the download
     */
    private Button downloadImage(long imageId) {
        Button downloadButton = new Button("Download Image");
        downloadButton.setEnabled(false);

        try{
            downloadButton.addClickListener(clickEvent -> {
                try {
                    String imagePath = omeroClient.downloadOmeTiff(imageId);
                    getUI().getPage().open(imagePath,"_blank");
                }
                catch (Exception e)
                {
                    LOG.error("Could not generate path to ome.tiff file for image: " + imageId);
                    LOG.debug(e);
                }
            });
            downloadButton.setEnabled(true);
        }catch(Exception e){
            throw new RuntimeException("",e);
        }
        return downloadButton;
    }

    /**
     * Generates a vaadin Window which displays the metadata information for the given
     * metadataProperties
     *
     * @param metadataProperties the metadataProperties containing the metadata of an image stored on
     *     the omero server
     * @return a vaadin Window
     */

    private Window metadataWindow(Collection<MetadataProperty> metadataProperties)
    {
        Window metadataWindow = new Window("Metadata Properties");
        VerticalLayout metadataLayout = new VerticalLayout();

        Grid<MetadataProperty> metadataGrid = new Grid<>();
        metadataGrid.setDataProvider(new ListDataProvider<MetadataProperty>(metadataProperties));
        metadataGrid.setSelectionMode(SelectionMode.NONE);

        Column<MetadataProperty, String> nameColumn = metadataGrid.addColumn(MetadataProperty::getName).setCaption("Name");
        Column<MetadataProperty, String> valueColumn = metadataGrid.addColumn(metadataProperty -> {
            return metadataProperty.getValue().toString();
        }).setCaption("Value");

        // remove the descriptionColumn, not needed atm
        //Column<MetadataProperty, String> descriptionColumn = metadataGrid.addColumn(MetadataProperty::getDescription).setCaption("Description");

        metadataLayout.addComponent(metadataGrid);

        metadataWindow.setContent(metadataLayout);
        metadataWindow.setModal(true);
        metadataWindow.setResizable(false);
        metadataWindow.center();

        return metadataWindow;
    }

    private Window imageViewerWindow(String requestUrl)
    {
        Window imageWindow = new Window("Image Viewer");
        VerticalLayout imageLayout = new VerticalLayout();
        imageLayout.setSizeFull();

        Resource fullImage = new ExternalResource(requestUrl);

        BrowserFrame browser = new BrowserFrame("", fullImage);
        browser.setSizeFull();
        imageLayout.addComponent(browser);

        imageWindow.setContent(imageLayout);
        imageWindow.setModal(true);
        imageWindow.setResizable(true);
        imageWindow.center();
        imageWindow.setWidth("1500px");
        imageWindow.setHeight("1000px");

        return imageWindow;
    }

    /**
     * Collects and converts the metadata stored on the omero server for a given imageId into a MetadataProperty Object
     *
     * @param imageId the image for which the metadata should be collected
     * @return Collection of MetadataProperty Objects
     */

    private Collection<MetadataProperty> collectMetadata(long imageId) {

        Collection<MetadataProperty> metadataProperties = new ArrayList<>();
        try {
            List metadataList = omeroClient.fetchMapAnnotationDataForImage(imageId);
            for (int i = 0; i < metadataList.size(); i++) {
                MapAnnotationData currentMapAnnotation = (MapAnnotationData) metadataList.get(i);
                List<NamedValue> list = (List<NamedValue>) currentMapAnnotation.getContent();
                for (NamedValue namedValue : list) {
                    String metaDataKey = namedValue.name;
                    String metaDataValue = namedValue.value;
                    metadataProperties.add(new MetadataProperty<String>(metaDataKey, metaDataValue, "None"));
                }
            }

        } catch (Exception e) {
            LOG.error("Could not retrieve metadata for image:" + imageId);
            LOG.debug(e);

        }
        return metadataProperties;
    }


    /**
     * Generates a vaadin Button which opens a Window displaying the metadata information for a given imageId
     *
     * @param imageId the image for which the Button should be generated
     * @return a vaadin Button
     */

    private Button metadataButton(long imageId) {
        Button metadataButton = new Button("Show Metadata");
        metadataButton.setEnabled(false);
        Collection<MetadataProperty> metadataProperties;

        metadataProperties = collectMetadata(imageId);
        if (!metadataProperties.isEmpty()) {
            metadataButton.setEnabled(true);
        }
        else {
            return metadataButton;
        }

        metadataButton.addClickListener(clickEvent -> {
            try {
                Window metadataWindow = metadataWindow(metadataProperties);
                addWindow(metadataWindow);
            }
            catch (Exception e)
            {
                LOG.error("Could not generate metadata subwindow for imageId: " + imageId);
                LOG.debug(e);
            }
        });
        return metadataButton;
    }

    private Button imageLinkButton(long imageId) {
        Button linkButton = new Button("View Image");
        //linkButton.setEnabled(false);

        String requestUrl = omeroClient.composeImageDetailAddress(imageId);
        linkButton.setEnabled(true);

        linkButton.addClickListener(clickEvent -> {
            try {
                Window imageWindow = imageViewerWindow(requestUrl);
                addWindow(imageWindow);
            }
            catch (Exception e)
            {
                LOG.error("Could not generate image viewer subwindow for imageId: " + imageId);
                LOG.debug(e);
            }
        });
        return linkButton;
    }

    private void refreshGrid(Grid<?> grid) {
        grid.getDataProvider().refreshAll();
        grid.setSizeFull();
    }

    /**
     * This method creates a TextField to filter a given column
     * @param dataProvider a {@link ListDataProvider} on which the filtering is applied on
     * @param column the column to be filtered
     * @param headerRow a {@link HeaderRow} to the corresponding {@link Grid}
     */
    private <T> void setupColumnFilter(ListDataProvider<T> dataProvider,
        Column<T, String> column, HeaderRow headerRow) {
        TextField filterTextField = new TextField();
        filterTextField.addValueChangeListener(event -> {
            dataProvider.addFilter(element ->
                StringUtils.containsIgnoreCase(column.getValueProvider().apply(element), filterTextField.getValue())
            );
        });
        filterTextField.setValueChangeMode(ValueChangeMode.EAGER);

        headerRow.getCell(column).setComponent(filterTextField);
        filterTextField.setSizeFull();
    }
}