/*
 * The MIT License
 *
 * Copyright (c) 2011, Dominik Bartholdi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.maven.reporters;

import static org.junit.Assert.assertEquals;

import hudson.EnvVars;
import hudson.maven.Maven36xBuildTest;
import hudson.maven.MavenJenkinsRule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tasks.Mailer;
import hudson.tasks.Mailer.DescriptorImpl;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;

import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.mock_javamail.Mailbox;

import java.util.List;

/**
 * 
 * @author imod (Dominik Bartholdi)
 * @author mrebasti
 *
 */
public class MavenMailerTest {

	private static final String EMAIL_JENKINS_CONFIGURED = "jenkins.configured.mail@domain.org";
	private static final String EMAIL_ADMIN = "\"me <me@sun.com>\"";
	private static final String EMAIL_SOME = "some.email@domain.org";
	private static final String EMAIL_OTHER = "other.email@domain.org";
	private static final String ENV_EMAILS_VARIABLE = "ENV_EMAILS";
	private static final String ENV_EMAILS_VALUE = "another.email@domain.org";
	
	@Rule public JenkinsRule j = new MavenJenkinsRule();

	@Test
	@Bug(5695)
    public void testMulipleMails() throws Exception {

        // there is one module failing in the build, therefore we expect one mail for the failed module and one for the over all build status
        final Mailbox inbox = runMailTest(true);
        assertEquals(2, inbox.size());

    }

	@Test
    @Bug(5695)
    public void testSingleMails() throws Exception {

        final Mailbox inbox = runMailTest(false);
        assertEquals(1, inbox.size());

    }

    public Mailbox runMailTest(boolean perModuleEamil) throws Exception {

        final DescriptorImpl mailDesc = Jenkins.get().getDescriptorByType(Mailer.DescriptorImpl.class);

        // intentionally give the whole thin in a double quote
        Mailer.descriptor().setAdminAddress(EMAIL_ADMIN);

        String recipient = "you <you@sun.com>";
        Mailbox yourInbox = Mailbox.get(new InternetAddress(recipient));
        yourInbox.clear();

        Maven36xBuildTest.configureMaven36();
        MavenModuleSet mms = j.jenkins.createProject(MavenModuleSet.class, "p");
        mms.setGoals("-V test -Dmaven.compiler.target=1.8 -Dmaven.compiler.source=1.8");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/maven-multimodule-unit-failure.zip")));
        j.assertBuildStatus(Result.UNSTABLE, mms.scheduleBuild2(0).get());

        MavenMailer m = new MavenMailer();
        m.recipients = recipient;
        m.perModuleEmail = perModuleEamil;
        mms.getReporters().add(m);

        mms.scheduleBuild2(0).get();

        Address[] senders = yourInbox.get(0).getFrom();
        assertEquals(1, senders.length);
        assertEquals("me <me@sun.com>", senders[0].toString());

        return yourInbox;
    }
    
    
    /**
	 * Test using the list of recipients of TAG ciManagement defined in
	 * ModuleRoot for all the modules.
	 * 
	 * @throws Exception
	 */
    @Test
    @Issue({"JENKINS-1201", "JENKINS-50251"})
    public void testCiManagementNotificationRoot() throws Exception {
    	JenkinsLocationConfiguration.get().setAdminAddress(EMAIL_ADMIN);
        Mailbox yourInbox = Mailbox.get(new InternetAddress(EMAIL_SOME));
        Mailbox jenkinsConfiguredInbox = Mailbox.get(new InternetAddress(EMAIL_JENKINS_CONFIGURED));
        yourInbox.clear();
        jenkinsConfiguredInbox.clear();

        Maven36xBuildTest.configureMaven36();
        MavenModuleSet mms = j.jenkins.createProject(MavenModuleSet.class, "p");
        mms.setAssignedNode(j.createSlave());
        mms.setGoals("-V test -Dmaven.compiler.target=1.8 -Dmaven.compiler.source=1.8");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/JENKINS-1201-parent-defined.zip")));
        
        MavenMailer m = new MavenMailer();
        m.recipients = EMAIL_JENKINS_CONFIGURED;
        m.perModuleEmail = true;
        mms.getReporters().add(m);
        
        j.assertBuildStatus(Result.UNSTABLE, mms.scheduleBuild2(0).get());
        
        assertEquals(2, yourInbox.size());
        assertEquals(2, jenkinsConfiguredInbox.size());
        
        Message message = yourInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = yourInbox.get(1);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(1);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
    }
    
    /**
	 * Test using the list of recipients of TAG ciManagement defined in
	 * ModuleRoot for de root module, and the recipients defined in moduleA for
	 * moduleA.
	 * 
	 * @throws Exception
	 */
    @Test
    @Bug(6421)
    public void testCiManagementNotificationModule() throws Exception {
    	
    	JenkinsLocationConfiguration.get().setAdminAddress(EMAIL_ADMIN);
        Mailbox otherInbox = Mailbox.get(new InternetAddress(EMAIL_OTHER));
        Mailbox someInbox = Mailbox.get(new InternetAddress(EMAIL_SOME));
        Mailbox jenkinsConfiguredInbox = Mailbox.get(new InternetAddress(EMAIL_JENKINS_CONFIGURED));
        otherInbox.clear();
        someInbox.clear();
        jenkinsConfiguredInbox.clear();

        Maven36xBuildTest.configureMaven36();
        MavenModuleSet mms = j.jenkins.createProject(MavenModuleSet.class, "p");
        mms.setGoals("-V test -Dmaven.compiler.target=1.8 -Dmaven.compiler.source=1.8");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/JENKINS-1201-module-defined.zip")));
        MavenMailer m = new MavenMailer();
        m.recipients = EMAIL_JENKINS_CONFIGURED;
        m.perModuleEmail = true;
        mms.getReporters().add(m);
        
        j.assertBuildStatus(Result.FAILURE, mms.scheduleBuild2(0).get());

        assertEquals(1, otherInbox.size());
        assertEquals(1, someInbox.size());
        assertEquals(2, jenkinsConfiguredInbox.size());
        
        Message message = otherInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_OTHER, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = someInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(1);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
    }

    @Test
    public void testEnvironmentVariableMailBeingReplaced() throws Exception {
        Jenkins instance = j.getInstance();
        DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = instance.getGlobalNodeProperties();
        List<EnvironmentVariablesNodeProperty> envVarsNodePropertyList =
                globalNodeProperties.getAll(EnvironmentVariablesNodeProperty.class);

        EnvVars envVars = null;
        if (envVarsNodePropertyList == null || envVarsNodePropertyList.isEmpty()) {
            EnvironmentVariablesNodeProperty envVarsNodeProperty = new EnvironmentVariablesNodeProperty();
            globalNodeProperties.add(envVarsNodeProperty);
            envVars = envVarsNodeProperty.getEnvVars();
        } else {
            envVars = envVarsNodePropertyList.get(0).getEnvVars();
        }
        envVars.put(ENV_EMAILS_VARIABLE, ENV_EMAILS_VALUE);

        JenkinsLocationConfiguration.get().setAdminAddress(EMAIL_ADMIN);
        Mailbox someInbox = Mailbox.get(new InternetAddress(EMAIL_SOME));
        Mailbox otherInbox = Mailbox.get(new InternetAddress(EMAIL_OTHER));
        Mailbox anotherInbox = Mailbox.get(new InternetAddress(ENV_EMAILS_VALUE));
        Mailbox jenkinsConfiguredInbox = Mailbox.get(new InternetAddress(EMAIL_JENKINS_CONFIGURED));
        someInbox.clear();
        otherInbox.clear();
        anotherInbox.clear();
        jenkinsConfiguredInbox.clear();

        Maven36xBuildTest.configureMaven36();
        MavenModuleSet mms = j.jenkins.createProject(MavenModuleSet.class, "p");
        mms.setGoals("-V test -Dmaven.compiler.target=1.8 -Dmaven.compiler.source=1.8");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/JENKINS-1201-module-defined.zip")));

        MavenMailer mailer1 = new MavenMailer();
        mailer1.recipients = EMAIL_JENKINS_CONFIGURED + " ${" + ENV_EMAILS_VARIABLE + "}";
        mailer1.perModuleEmail = true;
        mms.getReporters().add(mailer1);

        MavenModuleSetBuild mmsb = mms.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, mmsb);

        assertEquals(1, someInbox.size());
        assertEquals(2, anotherInbox.size());
        assertEquals(2, jenkinsConfiguredInbox.size());

        Message message = otherInbox.get(0);
        assertEquals(3, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_OTHER, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);

        message = someInbox.get(0);
        assertEquals(3, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);

        message = anotherInbox.get(0);
        assertEquals(3, message.getAllRecipients().length);
        assertContainsRecipient(ENV_EMAILS_VALUE, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
    }

    @Test
    @Bug(20209)
    public void testRecipientsNotNullAndMavenRecipientsNull () {
        MavenMailer fixture = new MavenMailer();
        fixture.recipients = "your-mail@gmail.com";
        fixture.mavenRecipients = null;
        
        assertEquals("your-mail@gmail.com", fixture.getAllRecipients());
    }
    
    @Test
    @Bug(20209)
    public void testMavenRecipientsNotNullAndRecipientsNull () {
        MavenMailer fixture = new MavenMailer();
        fixture.recipients = null;
        fixture.mavenRecipients = "your-mail@gmail.com";
        
        assertEquals("your-mail@gmail.com", fixture.getAllRecipients());
    }
    
    @Test
    @Bug(20209)
    public void testMavenRecipientsAndRecipientsNotNull () {
        MavenMailer fixture = new MavenMailer();
        fixture.recipients = "your-mail@gmail.com";
        fixture.mavenRecipients = "your-other-mail@gmail.com";
        
        assertEquals("your-mail@gmail.com your-other-mail@gmail.com", fixture.getAllRecipients());
    }

	private void assertContainsRecipient(String email, Message message) throws Exception {
		assert email != null;
		assert !email.trim().equals("");
		boolean containRecipient = false;
		for (Address address: message.getAllRecipients()) {
			if (email.equals(address.toString())) {
				containRecipient = true;
				break;
			}
		}
		assert containRecipient;
	}

}
