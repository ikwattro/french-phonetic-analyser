package com.galerieslafayette.analyzer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.galerieslafayette.analyzer.client.BodyStringEncoder;
import com.galerieslafayette.analyzer.client.ESClient;
import com.galerieslafayette.analyzer.document.MyDocument;
import com.galerieslafayette.analyzer.document.SearchResponse;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Volume;
import feign.Feign;
import feign.auth.BasicAuthRequestInterceptor;
import feign.jackson.JacksonDecoder;
import feign.okhttp.OkHttpClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.ClassLoader.getSystemResource;

public class FrenchPhoneticIT {

    private String INDEX_TYPE = "my_type";

    private ESClient esClient;

    private static ElasticsearchContainer container;

    private ObjectMapper esClientMapper;

    @Before
    public void setup(){

        esClientMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

    }
    
    @Test
    public void test_analyzer_with_version_51X() throws IOException, InterruptedException, URISyntaxException {
        execute_test("5.1."+System.getProperty("es51X.version"), "es-5.1.X.zip", "my_type_mapping.json", "my_index_settings.json", null);
    }

    @Test
    public void test_analyzer_with_version_52X() throws IOException, InterruptedException, URISyntaxException {
        execute_test("5.2."+System.getProperty("es52X.version"), "es-5.2.X.zip", "my_type_mapping.json", "my_index_settings.json", null);
    }

    @Test
    public void test_analyzer_with_version_53X() throws IOException, InterruptedException, URISyntaxException {
        execute_test("5.3."+System.getProperty("es53X.version"), "es-5.3.X.zip", "my_type_mapping.json", "my_index_settings.json", null);
    }

    @Test
    public void test_analyzer_with_version_54X() throws IOException, InterruptedException, URISyntaxException {
        execute_test("5.4."+System.getProperty("es54X.version"), "es-5.4.X.zip", "my_type_mapping.json", "my_index_settings.json", null);
    }

    @Test
    public void test_analyzer_with_version_55X() throws IOException, InterruptedException, URISyntaxException {
        execute_test("5.5."+System.getProperty("es55X.version"), "es-5.5.X.zip", "my_type_mapping.json", "my_index_settings.json", null);
    }

    @Test
    public void test_analyzer_with_version_56X() throws IOException, InterruptedException, URISyntaxException {
        execute_test("5.6."+System.getProperty("es56X.version"), "es-5.6.X.zip", "my_type_mapping.json", "my_index_settings.json", null);
    }

    @Test
    public void test_analyzer_with_version_60X() throws IOException, InterruptedException, URISyntaxException {
        execute_test("6.0."+System.getProperty("es60X.version"), "es-6.0.X.zip", "my_type_mapping.json", "my_index_settings.json", null);
    }

    @After
    public void destroy(){
        if(container != null){
            container.close();
        }

    }
    private void execute_test(String esVersion, final String analyzerFileName, String mappingFileName, String indexSettingsFileName, Integer indexVersion) throws IOException, InterruptedException, URISyntaxException {
        container = (ElasticsearchContainer) new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:"+esVersion)
                .withCopyFileToContainer(MountableFile.forHostPath(Paths.get(getSystemResource(analyzerFileName).toURI()), Transferable.DEFAULT_FILE_MODE), "/usr/share/elasticsearch/"+analyzerFileName)
        ;

        container.addFileSystemBind("/tmp/plugins"+ ThreadLocalRandom.current().nextInt(0, 30000000 + 1), "/usr/share/elasticsearch/plugins", BindMode.READ_WRITE);
        container.start();

        Container.ExecResult execResult1 = container.execInContainer("chown", "elasticsearch:root", "/usr/share/elasticsearch/" + analyzerFileName);
        Assertions.assertThat(execResult1.getStderr()).isEmpty();

        Container.ExecResult execResult = container.execInContainer("/usr/share/elasticsearch/bin/elasticsearch-plugin", "install", "file:/usr/share/elasticsearch/"  + analyzerFileName);
        Assertions.assertThat(execResult.getStderr()).isEmpty();

        container.stop();
        container.start();

        String host = container.getHost().toString();
//        String host = "http://localhost:9200";

        esClient = Feign.builder()
                .client(new OkHttpClient())
                .decoder(new JacksonDecoder(esClientMapper))
                .encoder(new BodyStringEncoder(esClientMapper))
                .target(ESClient.class, host);


        String settings = IOUtils.resourceToString("my_index_settings.json", Charset.forName("UTF-8"), FrenchPhoneticIT.class.getClassLoader());
        esClient.createIndex(settings);
        String mapping = IOUtils.resourceToString("my_type_mapping.json", Charset.forName("UTF-8"), FrenchPhoneticIT.class.getClassLoader());
        esClient.applyMapping(mapping);

        MyDocument myDocument1 = new MyDocument();
        myDocument1.setId(1l);
        myDocument1.setContent("Harold Capitaine");
        MyDocument myDocument2 = new MyDocument();
        myDocument2.setId(1l);
        myDocument2.setContent("Yves Mathieu Rideau Baudin");
        MyDocument myDocument3 = new MyDocument();
        myDocument3.setId(1l);
        myDocument3.setContent("Alexandre Pocheau");
        MyDocument myDocument4 = new MyDocument();
        myDocument4.setId(1l);
        myDocument4.setContent("Jonathan Baranzin");

        esClient.addDocument(myDocument1, 1l);
        esClient.addDocument(myDocument2, 2l);
        esClient.addDocument(myDocument3, 3l);
        esClient.addDocument(myDocument4, 4l);

        SearchResponse response1 = esClient.search("arold");
        SearchResponse response2 = esClient.search("bodain");



        Assertions.assertThat(response1.getHits()).isNotNull();
        Assertions.assertThat(response1.getHits().getHits()).isNotEmpty();
        Assertions.assertThat(response1.getHits().getHits()).extracting("source").extracting("id").containsOnly(myDocument1.getId());

        Assertions.assertThat(response2.getHits()).isNotNull();
        Assertions.assertThat(response2.getHits().getHits()).isNotEmpty();
        Assertions.assertThat(response2.getHits().getHits()).extracting("source").extracting("id").containsOnly(myDocument2.getId());
    }
}