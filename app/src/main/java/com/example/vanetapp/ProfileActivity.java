package com.example.vanetapp;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class ProfileActivity extends AppCompatActivity implements View.OnClickListener, IProfile {
    TextView usernameProfile, emailProfile, phoneProfile;
    FirebaseAuth firebaseAuth;
    FirebaseFirestore firebaseFirestore;
    String userId;

    private static final String TAG = "ProfileActivity";


    //widgets
    private ImageView mAvatarImage;

    //vars
    private ImageListFragment mImageListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mAvatarImage = findViewById(R.id.image_choose_avatar);
        usernameProfile = findViewById(R.id.usernameTextView);
        emailProfile = findViewById(R.id.emailTextView);
        phoneProfile = findViewById(R.id.phoneTextView);

        findViewById(R.id.image_choose_avatar).setOnClickListener(this);
        findViewById(R.id.text_choose_avatar).setOnClickListener(this);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();
        userId = firebaseAuth.getCurrentUser().getUid();

        DocumentReference documentReference = firebaseFirestore
                .collection("users")
                .document(userId);

        documentReference.addSnapshotListener(this, (documentSnapshot, error) -> {
            phoneProfile.setText(documentSnapshot.getString("phone"));
            usernameProfile.setText(documentSnapshot.getString("username"));
            emailProfile.setText(documentSnapshot.getString("email"));

        });


        retrieveProfileImage();

    }

    private void retrieveProfileImage(){
        RequestOptions requestOptions = new RequestOptions()
                .error(R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar);

        int avatar = 0;
        try{
            avatar = Integer.parseInt(((UserClient)getApplicationContext()).getUser().getAvatar());
        }catch (NumberFormatException e){
            Log.e(TAG, "retrieveProfileImage: no avatar image. Setting default. " + e.getMessage() );
        }

        Glide.with(ProfileActivity.this)
                .setDefaultRequestOptions(requestOptions)
                .load(avatar)
                .into(mAvatarImage);
    }

    @Override
    public void onClick(View v) {
        mImageListFragment = new ImageListFragment();
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_in_down,
                        R.anim.slide_out_down, R.anim.slide_out_up)
                .replace(R.id.fragment_container, mImageListFragment, "Image List")
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case android.R.id.home:{
                finish();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onImageSelected(int resource) {

        // remove the image selector fragment
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_in_down,
                        R.anim.slide_out_down, R.anim.slide_out_up)
                .remove(mImageListFragment)
                .commit();

        // display the image
        RequestOptions requestOptions = new RequestOptions()
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar);

        Glide.with(this)
                .setDefaultRequestOptions(requestOptions)
                .load(resource)
                .into(mAvatarImage);

        // update the client and database
        User user = ((UserClient)getApplicationContext()).getUser();
        user.setAvatar(String.valueOf(resource));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(FirebaseAuth.getInstance().getUid())
                .set(user);
    }


}