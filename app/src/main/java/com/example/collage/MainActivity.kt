package com.example.collage

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Date

const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var mainView: View
    private lateinit var imageButton1: ImageButton
    private lateinit var uploadImageFab: FloatingActionButton
    private lateinit var uploadProgressBar: ProgressBar

    // generated before picture is taken
    private var newPhotoPath: String? = null
    // access picture is one is taken
    private var visibleImagePath: String? = null
    private var imageFilename: String? = null
    private var photoUri: Uri? = null

    // bundle keys
    private val NEW_PHOTO_PATH_KEY = "new photo path key"
    private val VISIBLE_IMAGE_PATH_KEY = "visible image path key"

    private val cameraActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result -> handleImage(result)
    }

    private lateinit var storage: FirebaseStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Log.d(TAG, "onCreate $newPhotoPath")

        storage = Firebase.storage

        newPhotoPath = savedInstanceState?.getString(NEW_PHOTO_PATH_KEY)
        visibleImagePath = savedInstanceState?.getString(VISIBLE_IMAGE_PATH_KEY)

        mainView = findViewById(R.id.content)
        imageButton1 = findViewById(R.id.imageButton1)
        uploadImageFab = findViewById(R.id.upload_image)
        uploadProgressBar = findViewById(R.id.upload_progress_bar)

        imageButton1.setOnClickListener {
            takePicture()
        }

        uploadImageFab.setOnClickListener {
            Log.d(TAG, "Attempting to upload $imageFilename...")
            uploadImage()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // when device is rotated, photo remains visible
        outState.putString(NEW_PHOTO_PATH_KEY, newPhotoPath)
        outState.putString(VISIBLE_IMAGE_PATH_KEY, visibleImagePath)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "on window focus changed, visible image is $visibleImagePath")
        if (hasFocus) {
            visibleImagePath?.let { loadImage(imageButton1, it) }
        }
    }

    private fun takePicture() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val (photoFile, photoFilePath) = createImageFile()
        if (photoFile != null) {
            newPhotoPath = photoFilePath
            photoUri = FileProvider.getUriForFile(
                this,
                "com.example.collage.fileprovider",
                photoFile
            )
            Log.d(TAG, "$photoUri\n$photoFilePath")
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            cameraActivityLauncher.launch(takePictureIntent)
        }
    }

    // returns a File and the absolute path of the File
    @SuppressLint("SimpleDateFormat")
    private fun createImageFile(): Pair<File?, String?> {
        return try {
            val dateTime = SimpleDateFormat("yyyyMMdd__HHmmss").format(Date())
            imageFilename = "COLLAGE_$dateTime"
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val file = File.createTempFile(imageFilename, ".jpg", storageDir)
            Log.d(TAG, "Image file created $imageFilename.jpg")
            // "to" keyword used for Pairs
            file to file.absolutePath
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating image file", ex)
            null to null
        }
    }

    private fun handleImage(result: ActivityResult) {
        when (result.resultCode) {
            RESULT_OK -> {
                Log.d(TAG, "Result ok, image at $newPhotoPath")
                visibleImagePath = newPhotoPath
            }
            RESULT_CANCELED -> {
                Log.d(TAG, "Result cancelled, no picture taken")
            }
        }
    }

    private fun loadImage(imageButton: ImageButton, photoFilePath: String) {
        Picasso.get()
            .load(File(photoFilePath))
            .error(android.R.drawable.stat_notify_error)  // shows error icon when error
            .fit()  // attempt resize to fit within ImageButton
            .centerCrop()  // crop to fit
            .into(imageButton, object: Callback {
                override fun onSuccess() {
                    Log.d(TAG, "successfully loaded $photoFilePath")
                }

                override fun onError(e: Exception?) {
                    Log.e(TAG, "error loading $photoFilePath", e)
                }
            })
    }

    private fun uploadImage() {
        if (photoUri != null && imageFilename != null) {
            uploadProgressBar.visibility = View.VISIBLE

            val imageStorageRootRef = storage.reference
            val imagesRef = imageStorageRootRef.child("images")
            val imageFileRef = imagesRef.child(imageFilename!!)

            imageFileRef.putFile(photoUri!!)
                .addOnCompleteListener {
                    Snackbar.make(this.mainView, "Image uploaded!", Snackbar.LENGTH_LONG).show()
                    uploadProgressBar.visibility = View.GONE
                }
                .addOnFailureListener {
                    Snackbar.make(this.mainView, "Error uploading image", Snackbar.LENGTH_LONG).show()
                    uploadProgressBar.visibility = View.GONE
                }
            // progress listeners are also a thing
        } else {
            Snackbar.make(this.mainView, "Take a picture first!", Snackbar.LENGTH_LONG).show()
        }
    }
}