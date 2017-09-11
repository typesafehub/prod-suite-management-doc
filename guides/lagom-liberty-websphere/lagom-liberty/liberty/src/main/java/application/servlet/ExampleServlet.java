package application.servlet;

import com.spotify.dns.DnsException;
import com.spotify.dns.DnsSrvResolver;
import com.spotify.dns.DnsSrvResolvers;
import com.spotify.dns.LookupResult;
import com.spotify.dns.statistics.DnsReporter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;


@WebServlet(urlPatterns = "/example")
public class ExampleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final DnsReporter REPORTER = new StdoutReporter();

    DnsSrvResolver resolver = DnsSrvResolvers.newBuilder()
            .cachingLookups(true)
            .retainingDataOnFailures(true)
            .metered(REPORTER)
            .dnsLookupTimeoutMillis(1000)
            .build();

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // #SERVICE_LOOKUP
        String lagom = System.getenv("LAGOM_SERVICE");

        try {
            List<LookupResult> nodes = resolver.resolve(lagom);

            LookupResult node = nodes.get(0);
            String requestURL = String.format("http://%s:%d/lagom?name=liberty", node.host(), node.port());

            URL url = new URL(requestURL);
            String lagomResponse = makeRequest(url);
            response.getWriter().append(lagomResponse);

        } catch (DnsException e) {
            response.getWriter().append("Could not contact Lagom Service.");
            response.getWriter().append(e.getMessage());
        } catch (Exception e) {
            response.getWriter().append("Could not contact Lagom Service.");
            response.getWriter().append(e.getMessage());
        }
        // #SERVICE_LOOKUP
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    private String makeRequest(URL url) throws Exception {

        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();

    }
}
