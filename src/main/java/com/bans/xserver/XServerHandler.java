// package com.winthier.bans.xserver;

// import com.mickare.xserver.XServerListener;
// import com.mickare.xserver.XServerManager;
// import com.mickare.xserver.annotations.XEventHandler;
// import com.mickare.xserver.events.XServerMessageIncomingEvent;
// import com.mickare.xserver.exceptions.NotInitializedException;
// import com.mickare.xserver.net.XServer;
// import com.mickare.xserver.net.XServer;
// import com.winthier.bans.Ban;
// import com.winthier.bans.BansPlugin;
// import java.io.ByteArrayInputStream;
// import java.io.ByteArrayOutputStream;
// import java.io.IOException;
// import java.io.ObjectInputStream;
// import java.io.ObjectOutputStream;

// public class XServerHandler implements XServerListener {
//     public final BansPlugin plugin;

//     public XServerHandler(BansPlugin plugin) {
//         this.plugin = plugin;
//     }

//     public void init() {
//         try {
//             XServerManager.getInstance().getEventHandler().registerListener(plugin, this);
//         } catch (NotInitializedException nie) {
//             nie.printStackTrace();
//             return;
//         }
//     }

//     @XEventHandler(sync = true, channel = "Bans")
//     public void onMessage(XServerMessageIncomingEvent event) {
//         try {
//             byte[] data = event.getMessage().getContent();
//             ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
//             Object obj = in.readObject();
//             if (!(obj instanceof Ban)) throw new RuntimeException("Received unexpected message: " + obj.getClass().getName());
//             plugin.playerListener.onRemoteBan((Ban)obj);
//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//     }

//     public void broadcast(Ban ban) {
//         try {
//             ByteArrayOutputStream baos = new ByteArrayOutputStream();
//             ObjectOutputStream out = new ObjectOutputStream(baos);
//             out.writeObject(ban);
//             byte[] data = baos.toByteArray();
//             XServer home = XServerManager.getInstance().getHomeServer();
//             for (XServer xs : XServerManager.getInstance().getServers()) {
//                 if (xs != home) {
//                     xs.sendMessage(XServerManager.getInstance().createMessage("Bans", data));
//                 }
//             }
//         } catch (IOException ioe) {
//             ioe.printStackTrace();
//         } catch (NotInitializedException nie) {
//             nie.printStackTrace();
//         }
//     }
// }
