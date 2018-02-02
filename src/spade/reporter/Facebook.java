/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.reporter;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.types.Comment;
import com.restfb.types.NamedFacebookType;
import com.restfb.types.Photo;
import com.restfb.types.Post;
import com.restfb.types.User;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

// The RestFB library uses a Graph API access token to get data from Facebook.
// More information about RestFB can be found at http://restfb.com/
// To obtain the access token, go to https://developers.facebook.com/tools/explorer,
// click "Get Access Token" and check all permissions.
//
// The access token is the only argument required by the Facebook reporter.
// E.g., in the SPADE control client, usage would be as follows:
//       --> add reporter Facebook <ACCESS_TOKEN>
//
// Note that this reporter makes numerous REST calls to Facebook's Graph API
// and therefore takes a significant amount of time to completely fetch data
// for the user. The actual time taken will depend on the number of friends 
// the user has as well as the amount of activity.
/**
 *
 * @author Dawood Tariq and Muhammad Zaman
 */
public class Facebook extends AbstractReporter {

    Map<String, AbstractVertex> objects = new HashMap<>();
    List<String> postsVisited = new ArrayList<String>();
    FacebookClient client;
    String MY_ACCESS_TOKEN;
    Logger logger =  Logger.getLogger(Facebook.class.getName());

    @Override
    public boolean launch(String arguments) {
        MY_ACCESS_TOKEN = arguments;
        Runnable facebookProcessor = new Runnable() {
            @Override
            public void run() {
                // Start by fetching the user's own posts first. Posts include statuses,
                // links, photos, videos. Once the user's own posts are completely processed,
                // the reporter will iteratively fetch posts for the user's friends.
                client = new DefaultFacebookClient(MY_ACCESS_TOKEN);
                User me = client.fetchObject("me", User.class);
                String myId = me.getId();
                Process myVertex = getUserVertex(myId);
                processUserPhotos(myId);
                processUserPosts(myId);
                Connection<User> myFriends = client.fetchConnection("me/friends", User.class);

                for (List<User> myFriend : myFriends) {
                    for (User friend : myFriend) {
                        String friendId = friend.getId();
                        Process friendVertex = getUserVertex(friendId);
                        WasTriggeredBy wtb = new WasTriggeredBy(friendVertex, myVertex);
                        putEdge(wtb);
                        processUserPosts(friendId);
                        processUserPhotos(friendId);
                    }
                }
            }
        };
        Thread facebookThread = new Thread(facebookProcessor, "Facebook-Thread");
        facebookThread.start();

        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }

    public Process getUserVertex(String userId) {
        // A user vertex is created from the userId and cached for later reference
        // to prevent repetitive REST calls.
        if(userId == null){
            logger.log(Level.WARNING, "a null value was recived as userId, this is probably because of a deleted facebook account");
            return null;
        }else if (objects.containsKey(userId)) {
            return (Process) objects.get(userId);
        }
        User user=null;
        try{
            user = client.fetchObject(userId, User.class);
            Process process = new Process();
            process.addAnnotation("userId", user.getId());
            process.addAnnotation("name", user.getName());
            objects.put(userId, process);
            putVertex(process);
            return process;
        }catch(Exception e){
            logger.log(Level.WARNING, "userID = "+userId+" a graph request failed in getUserVertex, probably because of a deleted facebook account or a bad request");
        }
        return null;
    }

    public void processUserPosts(String userId) {
        // For each post, determine the type and add the appropriate annotations.
        // Once the post vertex has been created and added, process the tags in the post
        // (i.e., the "with" tags). Finally, process the likes and comments for this post.
        Connection<Post> userPosts = client.fetchConnection(userId + "/posts", Post.class);
        for (List<Post> userPostsPage : userPosts) {
            for (Post post : userPostsPage) {
                String fromUserId = post.getFrom().getId();
                Process userProcess = getUserVertex(fromUserId);
                if(userProcess == null){
                    return;
                }
                if(postsVisited.contains(post.getId())){
                    System.out.println("skipped a post because it was in the photos");
                    continue;

                }
                postsVisited.add(post.getId());

                Artifact postArtifact = new Artifact();
                WasGeneratedBy wgb = new WasGeneratedBy(postArtifact, userProcess);

                postArtifact.addAnnotation("objectId", post.getId());
                postArtifact.addAnnotation("author", userProcess.getAnnotation("name"));
                postArtifact.addAnnotation("message", post.getMessage());
                postArtifact.addAnnotation("time", post.getCreatedTime().toString());

                String postType = post.getType();
                switch (postType) {
                    case "status":
                        postArtifact.addAnnotation("fbType", "status");
                        wgb.addAnnotation("fbType", "status");
                        break;
                    case "link":
                        postArtifact.addAnnotation("linkUrl", post.getLink());
                        postArtifact.addAnnotation("fbType", "link");
                        wgb.addAnnotation("fbType", "link");
                        break;
                    case "photo":
                        postArtifact.addAnnotation("photoUrl", post.getLink());
                        postArtifact.addAnnotation("fbType", "photo");
                        wgb.addAnnotation("fbType", "photo");
                        break;
                    case "video":
                        postArtifact.addAnnotation("videoUrl", post.getLink());
                        postArtifact.addAnnotation("fbType", "video");
                        wgb.addAnnotation("fbType", "video");
                        break;
                    default:
                        break;
                }

                //get the privacy settings of the post
                Post.Privacy privacy = post.getPrivacy();
                postArtifact.addAnnotation("privacyValue", privacy.getValue());
                postArtifact.addAnnotation("privacyAllow", privacy.getAllow());
                postArtifact.addAnnotation("privacyDeny", privacy.getDeny());
                postArtifact.addAnnotation("privacyDescription", privacy.getDescription());
                postArtifact.addAnnotation("privacyFriends", privacy.getFriends());
                postArtifact.addAnnotation("privacyNetworks", privacy.getNetworks());

                objects.put(post.getId(), postArtifact);
                putVertex(postArtifact);
                putEdge(wgb);

                //get message tags and with tags from every post
                for (NamedFacebookType user : post.getWithTags()) {
                    Process taggedUser = getUserVertex(user.getId());
                    if(taggedUser == null){
                        continue;
                    }
                    Used used = new Used(taggedUser, postArtifact);
                    used.addAnnotation("fbType", "tagged with");
                    putEdge(used);
                }
                for (List<Post.MessageTag> tags : post.getMessageTags().values()) {
                    for(Post.MessageTag tag : tags){
                        Process taggedUser = getUserVertex(tag.getId());
                        if(taggedUser == null){
                            continue;
                        }
                        Used used = new Used(taggedUser, postArtifact);
                        used.addAnnotation("fbType", "tagged in");
                        putEdge(used);
                    }
                }


                processLikes(post.getId());
                processComments(post.getId());

            }
        }
    }
    public void processUserPhotos(String userId) {
        // For each post, determine the type and add the appropriate annotations.
        // Once the post vertex has been created and added, process the tags in the post
        // (i.e., the "with" tags). Finally, process the likes and comments for this post.
        Connection<Photo> userPhotos = client.fetchConnection(userId + "/photos", Photo.class);
        for (List<Photo> userPhotosPage : userPhotos) {
            for (Photo post : userPhotosPage) {
                String fromUserId = post.getFrom().getId();
                Process userProcess = getUserVertex(fromUserId);
                if(userProcess == null){
                    return;
                }
                if(postsVisited.contains(post.getId())){
                    System.out.println("skipped a photo because it was in the posts");
                    continue;
                }
                postsVisited.add(post.getId());
                Artifact postArtifact = new Artifact();
                WasGeneratedBy wgb = new WasGeneratedBy(postArtifact, userProcess);

                postArtifact.addAnnotation("objectId", post.getId());
                postArtifact.addAnnotation("author", userProcess.getAnnotation("name"));
                postArtifact.addAnnotation("message", post.getName());
                postArtifact.addAnnotation("time", post.getCreatedTime().toString());
                postArtifact.addAnnotation("photoUrl", post.getLink());
                postArtifact.addAnnotation("fbType", "photo");
                wgb.addAnnotation("fbType", "photo");


                objects.put(post.getId(), postArtifact);
                putVertex(postArtifact);
                putEdge(wgb);


                for (Photo.Tag tag : post.getTags()) { 
                    Process taggedUser = getUserVertex(tag.getId());
                    if(taggedUser == null){
                        continue;
                    }
                    Used used = new Used(taggedUser, postArtifact);
                    used.addAnnotation("fbType", "tagged in");
                    putEdge(used);
                }
                processLikes(post.getId());
                processComments(post.getId());

            }
        }
    }

    public void processComments(String objectId) {
        // Comments will belong to a post so in addition to having an edge to the author
        // of the comment, there will be an edge to the parent post as well. Finally, the
        // comment may have likes which are handled in the processLikes() method.
        Connection<Comment> commentPages = client.fetchConnection(objectId + "/comments", Comment.class);

        for (List<Comment> comments : commentPages) {
            for (Comment comment : comments) {
                String fromUserId = comment.getFrom().getId();
                Process userProcess = getUserVertex(fromUserId);

                if(userProcess == null){
                    continue;
                }

                Artifact commentArtifact = new Artifact();
                commentArtifact.addAnnotation("objectId", comment.getId());
                commentArtifact.addAnnotation("author", userProcess.getAnnotation("name"));
                commentArtifact.addAnnotation("message", comment.getMessage());
                commentArtifact.addAnnotation("time", comment.getCreatedTime().toString());
                commentArtifact.addAnnotation("fbType", "comment");
                objects.put(comment.getId(), commentArtifact);
                putVertex(commentArtifact);

                WasGeneratedBy wgb = new WasGeneratedBy(commentArtifact, userProcess);
                wgb.addAnnotation("fbType", "comment");
                putEdge(wgb);

                WasDerivedFrom wdf = new WasDerivedFrom(commentArtifact, (Artifact) objects.get(objectId));
                wdf.addAnnotation("fbType", "commentParent");
                putEdge(wdf);

                processLikes(comment.getId());
            }
        }
    }

    public void processLikes(String objectId) {
        // Likes are processed for each object (post or comment). A like is represented
        // by an edge from the user to the object.
        Connection<User> userPages = client.fetchConnection(objectId + "/likes", User.class);
        for (List<User> users : userPages) {
            for (User user : users) {
                Process userProcess = getUserVertex(user.getId());

                if(userProcess == null){
                    continue;
                }
                Artifact post = (Artifact) objects.get(objectId);
                WasGeneratedBy wgb = new WasGeneratedBy(post, userProcess);
                wgb.addAnnotation("fbType", "like");
                putEdge(wgb);
            }
        }
    }

}
