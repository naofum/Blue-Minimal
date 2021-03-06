package de.baumann.thema;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import de.baumann.thema.helpers.AppInfo;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class FragmentRequest extends Fragment {

    @SuppressWarnings("unchecked")
    private final ArrayList<String> list_activities = new ArrayList();
    @SuppressWarnings("unchecked")
    private static ArrayList<AppInfo> list_activities_final = new ArrayList();
    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private ViewSwitcher switcherLoad;
    private final FragmentRequest.AsyncWorkerList taskList = new AsyncWorkerList();
    private static final int BUFFER = 2048;
    private static final String SD = Environment.getExternalStorageDirectory().getAbsolutePath();

    private static final String SAVE_LOC = SD + "/BM_Icon-Request/files"; //TODO Set own file path.
    private static final String SAVE_LOC2 = SD + "/BM_Icon-Request"; //TODO Change also this one.
    private static final String appfilter_path = "empty_appfilter.xml"; //TODO Define path to appfilter.xml in assets folder.

    private static final String TAG = "RequestActivity";
    private static final boolean DEBUG = true; //TODO Set to false for PlayStore Release

    private View rootView;
    private SharedPreferences sharedPref;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.request, container, false);
        switcherLoad = (ViewSwitcher)rootView.findViewById(R.id.viewSwitcherLoadingMain);
        context = getActivity();

        sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPref.edit().putString("canClose", "false").apply();

        setHasOptionsMenu(true);

        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab_rq);
        fab.setImageResource(R.drawable.zip_box);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    int hasWRITE_EXTERNAL_STORAGE = getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (hasWRITE_EXTERNAL_STORAGE != PackageManager.PERMISSION_GRANTED) {
                        Snackbar snackbar = Snackbar
                                .make(switcherLoad, getString(R.string.permissions_granted), Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.yes), new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        Intent intent = new Intent();
                                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
                                        intent.setData(uri);
                                        getActivity().startActivity(intent);
                                    }
                                });
                        snackbar.show();
                    } else {
                        actionSend();
                    }
                } else {
                    actionSend();
                }
            }
        });

        if(taskList.getStatus() == AsyncTask.Status.PENDING){
            // My AsyncTask has not started yet
            taskList.execute();
        }

        if(taskList.getStatus() == AsyncTask.Status.FINISHED){
            // My AsyncTask is done and onPostExecute was called
            new AsyncWorkerList().execute();
        }

        return rootView;
    }

    public class AsyncWorkerList extends AsyncTask<String, Integer, String>{

        public AsyncWorkerList(){}

        @Override
        protected String doInBackground(String... arg0) {
            try {
                //Get already styled apps
                parseXML();
                // Compare them to installed apps
                prepareData();

                return null;
            }
            catch (Throwable e) {e.printStackTrace();}
            return null;
        }

        @Override
        protected void onPostExecute(String result) {

            // Display the unstyled app
            populateView(list_activities_final);
            //Switch from loading screen to the main view
            switcherLoad.showNext();
            sharedPref.edit().putString("canClose", "true").apply();

            super.onPostExecute(result);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        if(DEBUG) Log.v(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(savedInstanceState);
    }

    // Handler for sending messages out of separate Threads
    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case 0:
                    if(DEBUG)Log.v(TAG,"Handler case 0");
                    Snackbar.make(switcherLoad, R.string.request_toast_no_apps_selected, Snackbar.LENGTH_LONG).show();
                    return;

                case 1:
                    if(DEBUG)Log.v(TAG,"Handler case 1");
                    Snackbar.make(switcherLoad, getString(R.string.request_toast_apps_selected), Snackbar.LENGTH_LONG).show();
                    return;

                case 2:
                    if(DEBUG)Log.v(TAG,"Handler case 1");
                    Snackbar.make(switcherLoad, "Make sure you copied appfilter.xml in assets folder!", Snackbar.LENGTH_LONG).show();
                    return;

                default:
            }
        }
    };

    private void actionSend() {

        Thread actionSend_Thread = new Thread() {

            @Override
            public void run() {

                final File save_loc = new File(SAVE_LOC);
                final File save_loc2 = new File(SAVE_LOC2);

                deleteDirectory(save_loc2); //This deletes old zips

                save_loc.mkdirs(); // recreates the directory
                save_loc2.mkdirs();

                ArrayList arrayList = list_activities_final;
                StringBuilder stringBuilderXML = new StringBuilder();
                int amount = 0;

                // Get all selected apps
                for (int i = 0; i < arrayList.size(); i++) {
                    if (((AppInfo)arrayList.get(i)).isSelected()) {

                        String iconName = (((AppInfo)arrayList.get(i)).getCode().split("/")[0].replace(".", "_") + "_" +((AppInfo)arrayList.get(i)).getCode().split("/")[1]).replace(".", "_");
                        if(DEBUG)Log.i(TAG, "iconName: " + iconName);

                        stringBuilderXML.append("<!-- ").append(((AppInfo) arrayList.get(i)).getName()).append(" -->\n<item component=\"ComponentInfo{").append(((AppInfo) arrayList.get(i)).getCode()).append("}\" drawable=\"").append(iconName).append("\"/>").append("\n");

                        Bitmap bitmap = ((BitmapDrawable)((AppInfo)arrayList.get(i)).getImage()).getBitmap();
                        FileOutputStream fOut;

                        try {
                            fOut = new FileOutputStream(SAVE_LOC + "/" + iconName + ".png");
                            bitmap.compress(Bitmap.CompressFormat.PNG,100,fOut);
                            fOut.flush();
                            fOut.close();
                        }
                        catch (FileNotFoundException e) {if(DEBUG)Log.v(TAG, "FileNotFoundException");}
                        catch (IOException e) {	if(DEBUG)Log.v(TAG, "IOException");}
                        amount++;
                    }
                }
                if (amount == 0){//When there's no app selected show a toast and return.
                    handler.sendEmptyMessage(0);

                } else {
                    // write zip and start email intent.
                    try {
                        FileWriter fstream = new FileWriter(SAVE_LOC + "/appfilter.xml");
                        BufferedWriter out = new BufferedWriter(fstream);
                        out.write(stringBuilderXML.toString());
                        out.close();
                    } catch (Exception e){ return;}


                    SimpleDateFormat date = new SimpleDateFormat("yy-MM-dd_HH-mm", Locale.getDefault());
                    String zipName = "Icon_request_" + date.format(new Date());

                    createZipFile(SD + "/" + zipName + ".zip");
                    String path = SD + "/BM_Icon-Request/";

                    deleteDirectory(save_loc); //This deletes all generated files except the zip
                    File dirTemp = new File(path);

                    if (dirTemp.exists()) {
                        String deleteCmd = "rm -r " + dirTemp;
                        Runtime runtime = Runtime.getRuntime();
                        try {
                            runtime.exec(deleteCmd);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    handler.sendEmptyMessage(1);
                }
            }
        };
        if(!actionSend_Thread.isAlive()) {
            //Prevents the thread to be executed twice (or more) times.
            actionSend_Thread.start();
        }
    }

    // Read the appfilter.xml from assets and get all activities
    private void parseXML() {

        try{
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser myparser = xmlFactoryObject.newPullParser();

            AssetManager am = context.getAssets();
            InputStream inputStream = am.open(appfilter_path);
            myparser.setInput(inputStream, null);

            int activity = myparser.getEventType();
            while (activity != XmlPullParser.END_DOCUMENT) {
                String name=myparser.getName();
                switch (activity){
                    case XmlPullParser.START_TAG:
                        break;
                    case XmlPullParser.END_TAG:
                        if(name.equals("item")) {
                            try	{
                                String tmp_act = myparser.getAttributeValue(null,"component").split("/")[1];
                                String t_activity= tmp_act.substring(0, tmp_act.length()-1);

                                String tmp_pack = myparser.getAttributeValue(null,"component").split("/")[0];
                                String t_package= tmp_pack.substring(14, tmp_pack.length());

                                list_activities.add(t_package + "/" + t_activity);

                                if(DEBUG)Log.v(TAG,"Added Styled App: \"" +t_package + "/" + t_activity+"\"");
                            }
                            catch(ArrayIndexOutOfBoundsException ignored){}
                        }
                        break;
                }
                activity = myparser.next();
            }
        } catch(IOException exIO){handler.sendEmptyMessage(2);
        } //Show toast when there's no appfilter.xml in assets
        catch(XmlPullParserException ignored){
        }
    }

    @SuppressWarnings("unchecked")
    private void prepareData() { // Sort the apps

        ArrayList<AppInfo> arrayList = new ArrayList();
        PackageManager pm = getActivity().getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN", null);
        intent.addCategory("android.intent.category.LAUNCHER");
        List list = pm.queryIntentActivities(intent, 0);
        Iterator localIterator = list.iterator();
        if(DEBUG)Log.v(TAG,"list.size(): "+list.size());

        for (int i = 0; i < list.size(); i++) {
            ResolveInfo resolveInfo = (ResolveInfo)localIterator.next();

            // This is the main part where the already styled apps are sorted out.
            if ((list_activities.indexOf(resolveInfo.activityInfo.packageName + "/" + resolveInfo.activityInfo.name) == -1)) {

                AppInfo tempAppInfo = new AppInfo(
                        resolveInfo.activityInfo.packageName + "/" + resolveInfo.activityInfo.name, //Get package/activity
                        resolveInfo.loadLabel(pm).toString(), //Get the app name
                        getHighResIcon(pm, resolveInfo) //Loads xxxhdpi icon, returns normal if it on fail
                        //Unselect icon per default
                );
                arrayList.add(tempAppInfo);

                // This is just for debugging
                if(DEBUG)Log.i(TAG,"Added app: " + resolveInfo.loadLabel(pm));
            } else {
                // This is just for debugging
                if(DEBUG)Log.v(TAG,"Removed app: " + resolveInfo.loadLabel(pm));
            }
        }

        Collections.sort(arrayList, new Comparator<AppInfo>() { //Custom comparator to ensure correct sorting for characters like and apps starting with a small letter like iNex
            @Override
            public int compare(AppInfo object1, AppInfo object2) {
                Locale locale = Locale.getDefault();
                Collator collator = Collator.getInstance(locale);
                collator.setStrength(Collator.TERTIARY);

                if(DEBUG)Log.v(TAG,"Comparing \""+object1.getName()+"\" to \"" + object2.getName()+"\"");

                return collator.compare(object1.getName(), object2.getName());
            }
        });

        list_activities_final = arrayList;
    }


    private Drawable getHighResIcon(PackageManager pm, ResolveInfo resolveInfo){

        Resources resources;
        Drawable icon;

        try {
            ComponentName componentName = new ComponentName(resolveInfo.activityInfo.packageName , resolveInfo.activityInfo.name);

            resources = pm.getResourcesForActivity(componentName);//Get resources for the activity

            int iconId = resolveInfo.getIconResource();//Get the resource Id for the activity icon

            if(iconId != 0) {
                icon = resources.getDrawableForDensity(iconId, 640, null); //Loads the icon at xxhdpi resolution or lower.
                return icon;
            }
            return resolveInfo.loadIcon(pm);

        } catch (PackageManager.NameNotFoundException e) {
            return resolveInfo.loadIcon(pm);//If it fails return the normal icon
        } catch (Resources.NotFoundException e) {
            return resolveInfo.loadIcon(pm);
        }
    }

    @SuppressWarnings("unchecked")
    private void populateView(ArrayList arrayListFinal){
        ArrayList<AppInfo> local_arrayList;
        local_arrayList = arrayListFinal;

        ListView grid = (ListView) rootView.findViewById(R.id.appgrid);

        assert grid != null;

        AppAdapter appInfoAdapter;


        appInfoAdapter = new AppAdapter(getActivity(), local_arrayList);
        grid.setAdapter(appInfoAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> AdapterView, View view, int position, long row) {

                AppInfo appInfo = (AppInfo)AdapterView.getItemAtPosition(position);
                CheckBox checker = (CheckBox)view.findViewById(R.id.CBappSelect);
                ViewSwitcher icon = (ViewSwitcher)view.findViewById(R.id.viewSwitcherChecked);

                checker.toggle();
                appInfo.setSelected(checker.isChecked());

                if(appInfo.isSelected()) {
                    if(DEBUG)Log.v(TAG,"Selected App: "+appInfo.getName());
                    if(icon.getDisplayedChild() == 0){
                        icon.showNext();
                    }
                } else {
                    if(DEBUG)Log.v(TAG,"Deselected App: "+appInfo.getName());
                    if(icon.getDisplayedChild() == 1){
                        icon.showPrevious();
                    }
                }
            }
        });
    }

    private class AppAdapter extends ArrayAdapter<AppInfo> {
        @SuppressWarnings("unchecked")
        private final ArrayList<AppInfo> appList = new ArrayList();

        public AppAdapter(Context context, ArrayList<AppInfo> adapterArrayList) {
            super(context, R.layout.request_item_list, adapterArrayList);
            appList.addAll(adapterArrayList);
        }
        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {

            ViewHolder holder;
            if (convertView == null) {
                convertView = ((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.request_item_list, parent, false);
                holder = new ViewHolder();
                holder.apkIcon = (ImageView) convertView.findViewById(R.id.IVappIcon);
                holder.apkName = (TextView) convertView.findViewById(R.id.TVappName);
                holder.apkPackage = (TextView) convertView.findViewById(R.id.TVappPackage);
                holder.checker = (CheckBox) convertView.findViewById(R.id.CBappSelect);
                holder.switcherChecked = (ViewSwitcher)convertView.findViewById(R.id.viewSwitcherChecked);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppInfo appInfo = appList.get(position);

            holder.apkPackage.setText(String.valueOf(appInfo.getCode().split("/")[0]+"/"+appInfo.getCode().split("/")[1]));
            holder.apkName.setText(appInfo.getName());
            holder.apkIcon.setImageDrawable(appInfo.getImage());

            holder.switcherChecked.setInAnimation(null);
            holder.switcherChecked.setOutAnimation(null);

            holder.checker.setChecked(appInfo.isSelected());

            return convertView;
        }
    }

    private class ViewHolder {
        TextView apkName;
        TextView apkPackage;
        ImageView apkIcon;
        CheckBox checker;
        ViewSwitcher switcherChecked;
    }

    //Zip Stuff. Better leave that Alone ^^

    private static void deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
    }

    private static void createZipFile(final String out_file) {
        final File f = new File(FragmentRequest.SAVE_LOC);
        if (!f.canRead() || !f.canWrite())
        {
            if(DEBUG)Log.d(TAG, FragmentRequest.SAVE_LOC + " cannot be compressed due to file permissions");
            return;
        }
        try {
            ZipOutputStream zip_out = new ZipOutputStream(
                    new BufferedOutputStream(
                            new FileOutputStream(out_file), BUFFER));
            zipFile(FragmentRequest.SAVE_LOC, zip_out, "");
            zip_out.close();
        }
        catch (FileNotFoundException e){ if(DEBUG)Log.e("File not found", e.getMessage());
        }
        catch (IOException e){ if(DEBUG)Log.e("IOException", e.getMessage());
        }
    }

    // StahP !! Turn around ! Nothing to see here!

    // keeps directory structure
    private static void zipFile(final String path, final ZipOutputStream out, final String relPath) throws IOException {

        final File file = new File(path);
        if (!file.exists()){if(DEBUG)Log.d(TAG, file.getName() + " does NOT exist!");return;}
        final byte[] buf = new byte[1024];
        final String[] files = file.list();
        if (file.isFile()) {

            try (FileInputStream in = new FileInputStream(file.getAbsolutePath())) {
                out.putNextEntry(new ZipEntry(relPath + file.getName()));
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.closeEntry();
                in.close();
            } catch (ZipException zipE) {
                if (DEBUG) Log.d(TAG, zipE.getMessage());
            } finally {
                if (out != null) //noinspection ThrowFromFinallyBlock
                    out.closeEntry();
            }
        } else if (files.length > 0) {// non-empty folder

            for (String file1 : files) {
                zipFile(path + "/" + file1, out, relPath + file.getName() + "/");
            }
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.color).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.help:
                SpannableString s;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    s = new SpannableString(Html.fromHtml(getString(R.string.help_request),Html.FROM_HTML_MODE_LEGACY));
                } else {
                    //noinspection deprecation
                    s = new SpannableString(Html.fromHtml(getString(R.string.help_request)));
                }
                Linkify.addLinks(s, Linkify.WEB_URLS);

                final AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.title_iconrequest)
                        .setMessage(s)
                        .setPositiveButton(getString(R.string.yes), null);
                dialog.show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}