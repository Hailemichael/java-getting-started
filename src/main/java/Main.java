import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import spark.ModelAndView;
import spark.template.freemarker.FreeMarkerEngine;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import static spark.Spark.*;

public class Main {
	
	private static final Logger logger = LogManager.getLogger(Main.class.getName());
	protected static DesiredCapabilities capabilities;
	private static boolean DEBUG = false;

	public static void main(String[] args) {

		port(Integer.valueOf(System.getenv("PORT")));
		staticFileLocation("/public");

		get("/hello", (req, res) -> "Hello World");

		get("/source", (request, response) -> {
			
			String url = "https://bostad.stockholm.se/Lista/?s=59.16621&n=59.62020&w=17.59878&e=18.69097&sort=annonserad-fran-desc&vanlig=1&bostadssnabben=1&korttid=1&minYta=30";
			String html = getPageSource(url);
			return html;
		});

		get("/", (request, response) -> {
			Map<String, Object> attributes = new HashMap<>();
			attributes.put("message", "Hello World!");

			return new ModelAndView(attributes, "index.ftl");
		}, new FreeMarkerEngine());

		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(System.getenv("JDBC_DATABASE_URL"));
		final HikariDataSource dataSource = (config.getJdbcUrl() != null) ? new HikariDataSource(config)
				: new HikariDataSource();

		get("/db", (req, res) -> {
			Map<String, Object> attributes = new HashMap<>();
			try (Connection connection = dataSource.getConnection()) {
				Statement stmt = connection.createStatement();
				stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)");
				stmt.executeUpdate("INSERT INTO ticks VALUES (now())");
				ResultSet rs = stmt.executeQuery("SELECT tick FROM ticks");

				ArrayList<String> output = new ArrayList<String>();
				while (rs.next()) {
					output.add("Read from DB: " + rs.getTimestamp("tick"));
				}

				attributes.put("results", output);
				return new ModelAndView(attributes, "db.ftl");
			} catch (Exception e) {
				attributes.put("message", "There was an error: " + e);
				return new ModelAndView(attributes, "error.ftl");
			}
		}, new FreeMarkerEngine());

	}

	public static String getPageSource(String url) throws MalformedURLException, ProtocolException, IOException,
			SecurityException, NoSuchElementException, TimeoutException {
		if (DEBUG)
			logger.info("User Home" + System.getProperty("user.home"));
		String pageSourceString = null;
		WebDriver driver = null;

		if (System.getProperty("debug") == "yes") {
			DEBUG = true;
		}

		try {
			boolean isvalid = checkUrlValidity(url);
			if (isvalid == true) {
				if (System.getProperty("os.name").contains("Linux")) {
					File file = new File(
							Thread.currentThread().getContextClassLoader().getResource("phantomjs").getFile());
					if (!file.canExecute()) {
						file.setExecutable(true);
					}
					System.setProperty("phantomjs.binary.path", file.getAbsolutePath());
					if (DEBUG)
						logger.info(System.getProperty("phantomjs.binary.path"));
				} else {
					File file = new File(
							Thread.currentThread().getContextClassLoader().getResource("phantomjs.exe").getFile());
					System.setProperty("phantomjs.binary.path", file.getAbsolutePath());
					if (DEBUG)
						logger.info(System.getProperty("phantomjs.binary.path"));
				}

				capabilities = new DesiredCapabilities();
				capabilities.setJavascriptEnabled(true);
				capabilities.setCapability("takesScreenshot", false);
				capabilities.setCapability("outputEncoding", "utf8");
				driver = new PhantomJSDriver(capabilities);
				driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);

				driver.get(url);

				pageSourceString = driver.getPageSource();

				if (DEBUG)
					logger.info("Page Source: " + pageSourceString);

			}

		} catch (SecurityException e) {
			logger.error("SecurityException thrown while setting phantomjs executable: " + e.getMessage());
			throw e;
		} catch (NoSuchElementException e) {// not required here
			logger.error("Exception occured in Phantomjs driver: " + e.getMessage());
			throw e;
		} catch (TimeoutException e) {
			logger.error("TimeoutException occured in Phantomjs driver: " + e.getMessage());
			throw e;
		} catch (MalformedURLException e) {
			logger.error("MalformedURLException thrown for the URL: " + url + "\nException details: " + e.getMessage());
			throw e;
		} catch (ProtocolException e) {
			logger.error("ProtocolException thrown while setting Http Method to HEAD for: " + url
					+ "\nException details: " + e.getMessage());
			throw e;
		} catch (IOException e) {
			logger.error("IOException thrown while opening connection to: " + url + "\nException details: "
					+ e.getMessage());
			throw e;
		} finally {
			driver.quit();
		}

		return pageSourceString;
	}
	
	private static boolean checkUrlValidity(String url) throws MalformedURLException, ProtocolException, IOException {
		boolean isValid = false;
		URL pageUrl = null;
		int responseCode = 0;
		pageUrl = new URL(url);
		HttpURLConnection huc = (HttpURLConnection) pageUrl.openConnection();
		if (DEBUG)
			logger.info("Making Http HEAD request to url: " + url);
		huc.setRequestMethod("HEAD");
		huc.connect();
		responseCode = huc.getResponseCode();
		if (DEBUG)
			logger.info("Response code for Head request: " + responseCode);
		if (responseCode == 200) {
			isValid = true;
		}
		return isValid;
	}

}
