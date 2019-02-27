package life.qbic.omero;

import java.util.Collection;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ProjectData;
import omero.gateway.model.DatasetData;
import omero.log.SimpleLogger;

import omero.gateway.facility.DataManagerFacility;
import omero.model.Dataset;
import omero.model.DatasetI;
import omero.model.ProjectDatasetLink;
import omero.model.ProjectDatasetLinkI;
import omero.model.Project;
import omero.model.ProjectI;
import omero.model.IObject;

import omero.model.NamedValue;
import omero.gateway.model.MapAnnotationData;

import omero.gateway.model.ImageData;
import java.util.Arrays;



/////////////////////////////////////////////////////

public class BasicOMEROClient {

    //////////////////
    /** Reference to the gateway.*/
    private Gateway gateway;
    /** The security context.*/
    private SecurityContext ctx;

    private String hostname = "134.2.183.129";
    private int port = 4064;

    private String username;
    private String password;

    private HashMap<Long, String> projectMap;
    private HashMap<Long, Set<DatasetData>> datasetMap;

    public BasicOMEROClient(String username, String password) {

        this.username = username;
        this.password = password;

//        try {
//
//
//            this.connect(this.hostname, this.port, username, password);
//
//            //Do something e.g. loading user's data.
//            //Load the projects/datasets owned by the user currently logged in.
//
//        } catch (Exception e) {
//            System.out.println(e);
//        }


    }

    private void connect(String hostname, int port, String username, String password) throws Exception {

        this.gateway = new Gateway(new SimpleLogger());
        LoginCredentials cred = new LoginCredentials();
        cred.getServer().setHostname(hostname);

        if (port > 0) {
            cred.getServer().setPort(port);
        }

        cred.getUser().setUsername(username);
        cred.getUser().setPassword(password);

        ExperimenterData user = gateway.connect(cred);
        this.ctx = new SecurityContext(user.getGroupId());
    }

    public void connect() {
        try {

            this.connect(this.hostname, this.port, this.username, this.password);
        } catch (Exception e) {
            System.out.println(e);
        }

    }

    public void disconnect() {
        this.gateway.disconnect();
    }

    public HashMap<Long, String> loadProjects() {

        this.projectMap = new HashMap<Long, String>();
        this.datasetMap = new HashMap<Long, Set<DatasetData>>();

        try {

            BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
            Collection<ProjectData> projects = browse.getProjects(ctx);

            Iterator<ProjectData> i = projects.iterator();
            ProjectData project;
            while (i.hasNext()) {
                project = i.next();

                String name = project.getName();
                long id = project.getId();

                this.projectMap.put(id, name);
                this.datasetMap.put(id, project.getDatasets());
            }

        } catch (Exception e) {
            System.out.println(e);
        }

        return this.projectMap;
    }

    public HashMap<Long, String> getDatasets(long projectId) {

        HashMap<Long, String> datasetList = new HashMap<Long, String>();

        Set<DatasetData> datasets = this.datasetMap.get(projectId);

        Iterator<DatasetData> iterator = datasets.iterator();
        DatasetData dataset;
        while(iterator.hasNext()) {
            dataset = iterator.next();
            datasetList.put(dataset.getId(), dataset.getName());
        }

        return datasetList;

    }

    public void createProject(String name, String desc){

        try {

            DataManagerFacility dm = gateway.getFacility(DataManagerFacility.class);

            Project proj = new ProjectI();
            proj.setName(omero.rtypes.rstring(name));
            proj.setDescription(omero.rtypes.rstring(desc));

            IObject r = dm.saveAndReturnObject(this.ctx, proj);

        } catch (Exception e) {
            System.out.println(e);
        }


    }

    public long createDataset(long projectId, String name, String desc){

        try {

            DataManagerFacility dm = gateway.getFacility(DataManagerFacility.class);

            Dataset dataset = new DatasetI();
            dataset.setName(omero.rtypes.rstring(name));
            dataset.setDescription(omero.rtypes.rstring(desc));
            ProjectDatasetLink link = new ProjectDatasetLinkI();
            link.setChild(dataset);
            link.setParent(new ProjectI(projectId, false));

            IObject r = dm.saveAndReturnObject(this.ctx, link);

            ProjectDatasetLink remote_link = (ProjectDatasetLink)r;
            return remote_link.getChild().getId().getValue();


        } catch (Exception e) {
            System.out.println(e);
            return -1;
        }


    }

    public void addMapAnnotationToProject(long projectId, String key, String value) {

        List<NamedValue> result = new ArrayList<NamedValue>();
        result.add(new NamedValue(key, value));

        MapAnnotationData data = new MapAnnotationData();
        data.setContent(result);
        //data.setDescription("Training Example");

        //Use the following namespace if you want the annotation to be editable
        //in the webclient and insight
        data.setNameSpace(MapAnnotationData.NS_CLIENT_CREATED);
        try {

            DataManagerFacility fac = gateway.getFacility(DataManagerFacility.class);
            fac.attachAnnotation(ctx, data, new ProjectData(new ProjectI(projectId, false)));

        } catch (Exception e) {
            System.out.println(e);
        }

    }

    public void addMapAnnotationToDataset(long datasetId, String key, String value) {

        List<NamedValue> result = new ArrayList<NamedValue>();
        result.add(new NamedValue(key, value));

        MapAnnotationData data = new MapAnnotationData();
        data.setContent(result);

        data.setNameSpace(MapAnnotationData.NS_CLIENT_CREATED);
        try {

            DataManagerFacility fac = gateway.getFacility(DataManagerFacility.class);
            fac.attachAnnotation(ctx, data, new DatasetData(new DatasetI(datasetId, false)));

        } catch (Exception e) {
            System.out.println(e);
        }

    }

    public HashMap<Long, String> getImages(long datasetId) {

        HashMap<Long, String> imageList = new HashMap<Long, String>();

        try{

            BrowseFacility browse = this.gateway.getFacility(BrowseFacility.class);
            Collection<ImageData> images = browse.getImagesForDatasets(ctx, Arrays.asList(datasetId));

            Iterator<ImageData> j = images.iterator();
            ImageData image;
            while (j.hasNext()) {
                image = j.next();
                imageList.put(image.getId(), image.getName());
            }

        } catch (Exception e) {
            System.out.println(e);
        }

        return imageList;
    }

}
