package org.keycloak.testsuite.adapter.servlet;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.keycloak.testsuite.adapter.AbstractServletsAdapterTest;
import org.keycloak.testsuite.adapter.filter.AdapterActionsFilter;
import org.keycloak.testsuite.adapter.page.CustomerDb;
import org.keycloak.testsuite.adapter.page.CustomerPortal;
import org.keycloak.testsuite.arquillian.annotation.AppServerContainer;
import org.keycloak.testsuite.updaters.ClientAttributeUpdater;
import org.keycloak.testsuite.utils.arquillian.ContainerConstants;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.keycloak.protocol.oidc.OIDCConfigAttributes.CLIENT_SESSION_MAX_LIFESPAN;

@AppServerContainer(ContainerConstants.APP_SERVER_UNDERTOW)
public class OIDCTimeoutsTest extends AbstractServletsAdapterTest {

    @Page
    protected CustomerPortal customerPortal;

    @Deployment(name = CustomerPortal.DEPLOYMENT_NAME)
    protected static WebArchive customerPortal() {
        return servletDeployment(CustomerPortal.DEPLOYMENT_NAME, CustomerServlet.class, ErrorServlet.class, ServletTestUtils.class);
    }

    @Deployment(name = CustomerDb.DEPLOYMENT_NAME)
    protected static WebArchive customerDb() {
        return servletDeployment(CustomerDb.DEPLOYMENT_NAME, AdapterActionsFilter.class, CustomerDatabaseServlet.class);
    }

    @Test
    public void testAuthenticationAfterClientSessionMax() throws Exception {
        try (ClientAttributeUpdater cau = ClientAttributeUpdater.forClient(adminClient, "demo", "customer-portal")
                .setAttribute(CLIENT_SESSION_MAX_LIFESPAN, "60")
                .update()
        ) {
            customerPortal.navigateTo();
            testRealmLoginPage.form().login("bburke@redhat.com", "password");

            setAdapterAndServerTimeOffset(61, customerPortal.toString());
            
            driver.navigate().refresh();
            assertThat(driver.getPageSource(), containsString("Stian Thorgersen"));
        }
    }
}
