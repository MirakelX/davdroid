package at.bitfire.davdroid.resource.mirakel;

import org.apache.http.impl.client.CloseableHttpClient;

import java.net.URISyntaxException;

import at.bitfire.davdroid.resource.RemoteCollection;
import at.bitfire.davdroid.webdav.DavMultiget;



public class CalDavList extends RemoteCollection<at.bitfire.davdroid.resource.mirakel.ToDo> {
    @Override
    protected String memberContentType() {
        return "text/calendar";
    }

    @Override
    protected DavMultiget.Type multiGetType() {
        return DavMultiget.Type.CALENDAR;
    }

    @Override
    protected at.bitfire.davdroid.resource.mirakel.ToDo newResourceSkeleton(String name, String ETag) {
        return new at.bitfire.davdroid.resource.mirakel.ToDo(name, ETag);
    }


    public CalDavList(CloseableHttpClient httpClient, String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
        super(httpClient, baseURL, user, password, preemptiveAuth);
    }
}
