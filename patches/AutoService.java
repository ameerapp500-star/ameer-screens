package com.carscreens.app;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoService extends MediaBrowserService {
  private static final String API="https://de1.api.radio-browser.info/json/";
  private static final int MAX_STATIONS=60, MAX_TRACKS=500;
  private MediaSession session;
  private MediaPlayer player;
  private AudioManager audioManager;
  private AudioFocusRequest focusRequest;
  private boolean hasAudioFocus, prepared;
  private final List<Track> queue=new ArrayList<>();
  private final Map<String,Track> knownTracks=new HashMap<>();
  private int currentIndex=-1;
  private String currentId;

  private static final class Track {
    final String id,title,artist,album;
    Track(String id,String title,String artist,String album){
      this.id=id;this.title=title;this.artist=artist;this.album=album;
    }
  }

  @Override public void onCreate(){
    super.onCreate();
    audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
    session=new MediaSession(this,"AmeerScreensMedia");
    session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    Intent open=new Intent(this,MainActivity.class);
    int flags=PendingIntent.FLAG_UPDATE_CURRENT;
    if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
    session.setSessionActivity(PendingIntent.getActivity(this,0,open,flags));
    session.setCallback(new MediaSession.Callback(){
      @Override public void onPlayFromMediaId(String id,Bundle extras){playId(id);}
      @Override public void onPlay(){
        if(player!=null&&prepared){if(requestFocus()){player.start();publishState(PlaybackState.STATE_PLAYING);}}
        else if(!TextUtils.isEmpty(currentId))playId(currentId);
      }
      @Override public void onPause(){pausePlayer();}
      @Override public void onStop(){stopPlayer();}
      @Override public void onSkipToNext(){skip(1);}
      @Override public void onSkipToPrevious(){skip(-1);}
      @Override public void onSeekTo(long p){
        if(player!=null&&prepared)try{player.seekTo((int)Math.max(0,p));publishState(player.isPlaying()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED);}catch(Exception ignored){}
      }
    });
    setSessionToken(session.getSessionToken());session.setActive(true);
    publishState(PlaybackState.STATE_NONE);
  }

  @Override public BrowserRoot onGetRoot(String pkg,int uid,Bundle hints){return new BrowserRoot("root",null);}

  private MediaBrowser.MediaItem item(String id,String title,String sub,int flags){
    MediaDescription.Builder b=new MediaDescription.Builder().setMediaId(id).setTitle(title);
    if(!TextUtils.isEmpty(sub))b.setSubtitle(sub);
    return new MediaBrowser.MediaItem(b.build(),flags);
  }

  @Override public void onLoadChildren(final String parent,final Result<List<MediaBrowser.MediaItem>> result){
    result.detach();
    new Thread(()->{
      List<MediaBrowser.MediaItem> out=new ArrayList<>();
      try{
        int B=MediaBrowser.MediaItem.FLAG_BROWSABLE,P=MediaBrowser.MediaItem.FLAG_PLAYABLE;
        if("root".equals(parent)){
          out.add(item("radio","الراديو","محطات إذاعية من الإنترنت",B));
          out.add(item("music","الموسيقى","الأغاني الموجودة على الهاتف",B));
        }else if("radio".equals(parent)){
          JSONArray a=new JSONArray(httpGet("countries?hidebroken=true"));
          List<JSONObject> countries=new ArrayList<>();
          for(int i=0;i<a.length();i++)countries.add(a.getJSONObject(i));
          Collections.sort(countries,(x,y)->y.optInt("stationcount")-x.optInt("stationcount"));
          for(int i=0;i<countries.size()&&i<60;i++){
            JSONObject c=countries.get(i);String code=c.optString("iso_3166_1");
            if(!TextUtils.isEmpty(code))out.add(item("country:"+code,c.optString("name")+" · "+c.optInt("stationcount"),"محطات الراديو",B));
          }
        }else if(parent.startsWith("country:")){
          String code=parent.substring(8);
          JSONArray a=new JSONArray(httpGet("stations/search?countrycode="+URLEncoder.encode(code,"UTF-8")+"&hidebroken=true&order=votes&reverse=true&limit="+MAX_STATIONS));
          List<Track> found=new ArrayList<>();
          for(int i=0;i<a.length();i++){
            JSONObject s=a.getJSONObject(i);String u=s.optString("url_resolved"),n=s.optString("name").trim();
            if(!TextUtils.isEmpty(u)&&!TextUtils.isEmpty(n)){Track t=new Track(u,n,"راديو مباشر","");found.add(t);out.add(item(u,n,"بث مباشر",P));}
          }
          synchronized(queue){queue.clear();queue.addAll(found);}
          synchronized(knownTracks){for(Track t:found)knownTracks.put(t.id,t);}
        }else if("music".equals(parent)){
          List<Track> found=loadMusic();
          synchronized(queue){queue.clear();queue.addAll(found);}
          synchronized(knownTracks){for(Track t:found)knownTracks.put(t.id,t);}
          for(Track t:found)out.add(item(t.id,t.title,TextUtils.isEmpty(t.artist)?"ملف صوتي":t.artist,P));
          if(found.isEmpty())out.add(item("empty-music","لا توجد ملفات صوتية","اسمح للتطبيق بالوصول إلى الموسيقى",P));
        }
      }catch(Exception ignored){}
      result.sendResult(out);
    },"AmeerCarBrowse").start();
  }

  private List<Track> loadMusic(){
    List<Track> out=new ArrayList<>();Uri base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;Cursor c=null;
    try{
      c=getContentResolver().query(base,new String[]{MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM},
        MediaStore.Audio.Media.IS_MUSIC+"!=0",null,MediaStore.Audio.Media.DATE_ADDED+" DESC");
      if(c!=null)while(c.moveToNext()&&out.size()<MAX_TRACKS){
        String title=c.getString(1);if(TextUtils.isEmpty(title))title="مقطع صوتي";
        out.add(new Track(ContentUris.withAppendedId(base,c.getLong(0)).toString(),title,c.getString(2),c.getString(3)));
      }
    }catch(SecurityException ignored){}finally{if(c!=null)c.close();}
    return out;
  }

  private String httpGet(String path)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();
    c.setRequestProperty("User-Agent","AmeerScreens/2.0 AndroidAuto");c.setConnectTimeout(10000);c.setReadTimeout(15000);
    try{
      BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));StringBuilder b=new StringBuilder();String l;
      while((l=r.readLine())!=null)b.append(l);r.close();return b.toString();
    }finally{c.disconnect();}
  }

  private void playId(String id){
    if(TextUtils.isEmpty(id)||"empty-music".equals(id))return;
    Track t;synchronized(knownTracks){t=knownTracks.get(id);}
    synchronized(queue){for(int i=0;i<queue.size();i++)if(id.equals(queue.get(i).id)){currentIndex=i;break;}}
    if(t==null)t=new Track(id,"Ameer Screens","","");
    currentId=id;releasePlayer();prepared=false;
    session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,id)
      .putString(MediaMetadata.METADATA_KEY_TITLE,t.title).putString(MediaMetadata.METADATA_KEY_ARTIST,t.artist==null?"":t.artist)
      .putString(MediaMetadata.METADATA_KEY_ALBUM,t.album==null?"":t.album).build());
    publishState(PlaybackState.STATE_BUFFERING);
    if(!requestFocus()){publishState(PlaybackState.STATE_PAUSED);return;}
    try{
      player=new MediaPlayer();
      player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
      player.setOnPreparedListener(mp->{
        prepared=true;
        try{
          MediaMetadata old=session.getController().getMetadata();MediaMetadata.Builder mb=new MediaMetadata.Builder();
          if(old!=null)mb.putString(MediaMetadata.METADATA_KEY_TITLE,old.getString(MediaMetadata.METADATA_KEY_TITLE)).putString(MediaMetadata.METADATA_KEY_ARTIST,old.getString(MediaMetadata.METADATA_KEY_ARTIST));
          mb.putLong(MediaMetadata.METADATA_KEY_DURATION,mp.getDuration());session.setMetadata(mb.build());
          mp.start();publishState(PlaybackState.STATE_PLAYING);
        }catch(Exception e){publishState(PlaybackState.STATE_ERROR);}
      });
      player.setOnCompletionListener(mp->skip(1));
      player.setOnErrorListener((mp,w,e)->{prepared=false;publishState(PlaybackState.STATE_ERROR);return true;});
      player.setDataSource(this,Uri.parse(id));player.prepareAsync();
    }catch(Exception e){releasePlayer();publishState(PlaybackState.STATE_ERROR);}
  }

  private boolean requestFocus(){
    if(audioManager==null)return true;
    AudioManager.OnAudioFocusChangeListener listener=change->{
      if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)pausePlayer();
      else if(change==AudioManager.AUDIOFOCUS_GAIN&&player!=null&&prepared)try{player.setVolume(1f,1f);player.start();publishState(PlaybackState.STATE_PLAYING);}catch(Exception ignored){}
      else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK&&player!=null)try{player.setVolume(.25f,.25f);}catch(Exception ignored){}
    };
    int res;
    if(Build.VERSION.SDK_INT>=26){
      focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener(listener).build();
      res=audioManager.requestAudioFocus(focusRequest);
    }else res=audioManager.requestAudioFocus(listener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
    hasAudioFocus=res==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;return hasAudioFocus;
  }

  private void abandonFocus(){if(audioManager!=null&&hasAudioFocus){if(Build.VERSION.SDK_INT>=26&&focusRequest!=null)audioManager.abandonAudioFocusRequest(focusRequest);hasAudioFocus=false;}}
  private void skip(int dir){
    String id=null;synchronized(queue){if(!queue.isEmpty()){currentIndex=currentIndex<0?(dir>0?0:queue.size()-1):(currentIndex+dir+queue.size())%queue.size();id=queue.get(currentIndex).id;}}
    if(id!=null)playId(id);
  }
  private void pausePlayer(){if(player!=null&&prepared){try{if(player.isPlaying())player.pause();}catch(Exception ignored){}publishState(PlaybackState.STATE_PAUSED);}}
  private void stopPlayer(){releasePlayer();abandonFocus();publishState(PlaybackState.STATE_STOPPED);}
  private void releasePlayer(){
    if(player!=null)try{player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.setOnErrorListener(null);player.release();}catch(Exception ignored){}
    player=null;prepared=false;
  }
  private void publishState(int state){
    if(session==null)return;long pos=0;if(player!=null&&prepared)try{pos=player.getCurrentPosition();}catch(Exception ignored){}
    long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_PLAY_FROM_MEDIA_ID|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;
    PlaybackState.Builder b=new PlaybackState.Builder().setActions(actions).setState(state,pos,state==PlaybackState.STATE_PLAYING?1f:0f);
    if(state==PlaybackState.STATE_ERROR)b.setErrorMessage("تعذر تشغيل هذا الملف أو المحطة");
    session.setPlaybackState(b.build());
  }
  @Override public void onDestroy(){stopPlayer();if(session!=null){session.setActive(false);session.release();session=null;}super.onDestroy();}
}