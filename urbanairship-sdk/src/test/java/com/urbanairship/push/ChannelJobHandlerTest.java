/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushUser;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ChannelJobHandlerTest extends BaseTestCase {
    private static final String CHANNEL_LOCATION_KEY = "Location";

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeResponseBody = "{\"channel_id\": \"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE\"}";


    private PreferenceDataStore dataStore;
    private PushManager pushManager;
    private ChannelApiClient client;
    private ChannelJobHandler jobHandler;
    private RichPushInbox richPushInbox;
    private RichPushUser richPushUser;
    private JobDispatcher mockDispatcher;

    @Before
    public void setUp() {
        client = mock(ChannelApiClient.class);
        mockDispatcher = mock(JobDispatcher.class);
        richPushInbox = mock(RichPushInbox.class);
        TestApplication.getApplication().setInbox(richPushInbox);

        richPushUser = mock(RichPushUser.class);
        when(richPushInbox.getUser()).thenReturn(richPushUser);

        pushManager = UAirship.shared().getPushManager();
        dataStore = TestApplication.getApplication().preferenceDataStore;


        // Extend it to make handleIntent public so we can call it directly
        jobHandler = new ChannelJobHandler(TestApplication.getApplication(), UAirship.shared(),
                TestApplication.getApplication().preferenceDataStore, mockDispatcher, client);
    }

    /**
     * Test update registration will create a new channel for Amazon platform
     */
    @Test
    public void testUpdateRegistrationCreateChannelAmazon() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION)
                                 .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        assertEquals("Channel ID should exist in preferences", fakeChannelId, pushManager.getChannelId());
        assertEquals("Channel location should exist in preferences", fakeChannelLocation,
                pushManager.getChannelLocation());
    }

    /**
     * Test update registration with channel ID and null channel location will create a new channel
     */
    @Test
    public void testUpdateRegistrationNullChannelLocation() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Only set the channel ID
        pushManager.setChannel(fakeChannelId, null);

        assertEquals("Channel ID should be set in preferences", fakeChannelId, pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        assertEquals("Channel ID should match in preferences", fakeChannelId, pushManager.getChannelId());
        assertEquals("Channel location should match in preferences", fakeChannelLocation,
                pushManager.getChannelLocation());
    }

    /**
     * Test creating channel accepts a 200
     */
    @Test
    public void testCreateChannel200() {
        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        assertEquals("Channel ID should match in preferences", fakeChannelId, pushManager.getChannelId());
        assertEquals("Channel location should match in preferences", fakeChannelLocation,
                pushManager.getChannelLocation());

        // Verify we update the user
        verify(richPushInbox.getUser()).update(true);
    }


    /**
     * Test update registration fail to create a channel when channel response code is not successful
     */
    @Test
    public void testUpdateRegistrationResponseCodeFail() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());
    }

    /**
     * Test update registration fail to create a channel when channel ID from response is null
     */
    @Test
    public void testUpdateRegistrationResponseNullChannelId() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(null);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());
    }

    /**
     * Test updating a channel succeeds
     */
    @Test
    public void testUpdateChannelSucceed() throws MalformedURLException {
        // Set Channel ID and channel location
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        assertEquals("Channel ID should exist in preferences", pushManager.getChannelId(), fakeChannelId);
        assertEquals("Channel location should exist in preferences", pushManager.getChannelLocation(), fakeChannelLocation);

        long lastRegistrationTime = dataStore.getLong("com.urbanairship.push.LAST_REGISTRATION_TIME", 0);

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        URL channelLocation = new URL(fakeChannelLocation);
        // Return the response
        when(client.updateChannelWithPayload(channelLocation, payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify channel update succeeded
        assertNotSame("Last registration time should be updated", dataStore.getLong("com.urbanairship.push.LAST_REGISTRATION_TIME", 0), lastRegistrationTime);
    }

    /**
     * Test updating channel returns a 409 recreates the channel.
     */
    @Test
    public void testChannelConflict() throws MalformedURLException {
        // Set Channel ID and channel location
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Set up a conflict response
        Response conflictResponse = mock(Response.class);
        when(conflictResponse.getStatus()).thenReturn(HttpURLConnection.HTTP_CONFLICT);
        when(client.updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class))).thenReturn(conflictResponse);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify update was called
        Mockito.verify(client).updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class));

        // Verify the channel was cleared
        assertNull("Channel ID should be null", pushManager.getChannelId());
        assertNull("Channel location should be null", pushManager.getChannelLocation());

        // Verify a new jobInfo to update the channel is dispatched
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(ChannelJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
            }
        }));
    }

    /**
     * Test apply tag group changes updates the pending tag groups.
     */
    @Test
    public void testApplyTagGroupChanges() throws JsonException {
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        JsonValue mutations = JsonValue.wrapOpt(Collections.singletonList(mutation));

        // Apply tag groups
        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_APPLY_TAG_GROUP_CHANGES)
                                 .putExtra(TagGroupsEditor.EXTRA_TAG_GROUP_MUTATIONS, mutations.toString())
                                 .build();

        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify pending tags are saved
        assertEquals(mutations, dataStore.getJsonValue(ChannelJobHandler.PENDING_TAG_GROUP_MUTATIONS_KEY));
    }

    /**
     * Test apply tag group changes schedules a tag group update request.
     */
    @Test
    public void testApplyTagGroupChangesSchedulesUpload() throws JsonException {
        // Return a named user ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        JsonValue mutations = JsonValue.wrapOpt(Collections.singletonList(mutation));

        // Apply tag groups
        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_APPLY_TAG_GROUP_CHANGES)
                                 .putExtra(TagGroupsEditor.EXTRA_TAG_GROUP_MUTATIONS, mutations.toString())
                                 .build();

        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify a new jobInfo to update tag group registration is dispatched
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(ChannelJobHandler.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
    }

    /**
     * Test update tag groups succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateTagGroups() throws JsonException {
        // Return a named user ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        JsonValue mutations = JsonValue.wrapOpt(Collections.singletonList(mutation));

        // Apply tag groups
        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_APPLY_TAG_GROUP_CHANGES)
                                 .putExtra(TagGroupsEditor.EXTRA_TAG_GROUP_MUTATIONS, mutations.toString())
                                 .build();

        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(client.updateTagGroups(fakeChannelId, mutation)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify update tag groups called
        Mockito.verify(client).updateTagGroups(fakeChannelId, mutation);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(ChannelJobHandler.PENDING_TAG_GROUP_MUTATIONS_KEY, null));
    }

    /**
     * Test update tag groups without a channel ID fails.
     */
    @Test
    public void testUpdateTagGroupsNoChannel() throws JsonException {
        // Return a null named user ID
        pushManager.setChannel(null, null);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        JsonValue mutations = JsonValue.wrapOpt(Collections.singletonList(mutation));

        // Apply tag groups
        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_APPLY_TAG_GROUP_CHANGES)
                                 .putExtra(TagGroupsEditor.EXTRA_TAG_GROUP_MUTATIONS, mutations.toString())
                                 .build();

        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Perform the update
        jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify update tag groups not called when channel ID doesn't exist
        verifyZeroInteractions(client);
    }

    /**
     * Test update tag groups fails if the status is 500.
     */
    @Test
    public void testUpdateTagGroupsServerError() throws JsonException {
        // Return a named user ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Provide pending changes
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        JsonValue mutations = JsonValue.wrapOpt(Collections.singletonList(mutation));

        // Apply tag groups
        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_APPLY_TAG_GROUP_CHANGES)
                                 .putExtra(TagGroupsEditor.EXTRA_TAG_GROUP_MUTATIONS, mutations.toString())
                                 .build();

        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(client.updateTagGroups(fakeChannelId, mutation)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_RETRY, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify update tag groups is called
        Mockito.verify(client).updateTagGroups(fakeChannelId, mutation);

        // Verify pending tags persist
        assertNotNull(dataStore.getString(ChannelJobHandler.PENDING_TAG_GROUP_MUTATIONS_KEY, null));
    }

    /**
     * Test don't update tag groups if pending tag group mutations are empty.
     */
    @Test
    public void testNoUpdateTagGroupsWithEmptyTags() {
        // Return a named user ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Clear pending changes
        dataStore.remove(NamedUserJobHandler.PENDING_TAG_GROUP_MUTATIONS_KEY);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(ChannelJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_FINISHED, jobHandler.performJob(new Job(jobInfo, true)));

        // Verify it didn't cause a client update
        verifyZeroInteractions(client);
    }
}
