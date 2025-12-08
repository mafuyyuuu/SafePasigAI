package com.capstone.safepasigai.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.model.EmergencyContact
import com.capstone.safepasigai.data.repository.ContactsRepository
import com.capstone.safepasigai.databinding.FragmentContactsBinding
import com.capstone.safepasigai.ui.adapter.EmergencyContactAdapter
import com.capstone.safepasigai.utils.ImageCropHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.yalantis.ucrop.UCrop
import java.io.File

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var contactsRepository: ContactsRepository
    private lateinit var adapter: EmergencyContactAdapter
    
    // For avatar picking in dialog
    private var pendingAvatarUri: String = ""
    private var currentDialogAvatarView: ImageView? = null
    private var currentDialogPlaceholder: ImageView? = null

    // Image picker launcher - opens gallery
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            // Launch UCrop for 1:1 cropping
            val cropIntent = ImageCropHelper.createCropIntent(requireContext(), selectedUri)
            cropLauncher.launch(cropIntent)
        }
    }
    
    // Crop launcher - handles UCrop result  
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedPath = ImageCropHelper.handleCropResult(result.resultCode, result.data)
            if (croppedPath != null) {
                // Save to internal storage
                val savedPath = ImageCropHelper.saveCroppedImage(requireContext(), croppedPath, "contact_avatar")
                if (savedPath != null) {
                    pendingAvatarUri = savedPath
                    currentDialogAvatarView?.let { imgView ->
                        imgView.visibility = View.VISIBLE
                        currentDialogPlaceholder?.visibility = View.GONE
                        Glide.with(this)
                            .load(File(savedPath))
                            .circleCrop()
                            .into(imgView)
                    }
                } else {
                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            Toast.makeText(context, "Crop failed: ${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        contactsRepository = ContactsRepository(requireContext())
        
        setupRecyclerView()
        setupListeners()
        loadContacts()
    }

    private fun setupRecyclerView() {
        adapter = EmergencyContactAdapter(
            onCallClick = { contact -> callContact(contact) },
            onMoreClick = { contact, anchor -> showContactMenu(contact, anchor) }
        )
        
        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactsFragment.adapter
        }
    }

    private fun setupListeners() {
        binding.fabAddContact.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun loadContacts() {
        val contacts = contactsRepository.getContacts()
        
        if (contacts.isEmpty()) {
            binding.rvContacts.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.rvContacts.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            adapter.submitList(contacts)
        }
    }

    private fun showAddContactDialog(existingContact: EmergencyContact? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val phoneInput = dialogView.findViewById<TextInputEditText>(R.id.etPhone)
        val relationshipInput = dialogView.findViewById<TextInputEditText>(R.id.etRelationship)
        val avatarCard = dialogView.findViewById<MaterialCardView>(R.id.cardAvatar)
        val avatarImage = dialogView.findViewById<ImageView>(R.id.imgAvatar)
        val placeholderImage = dialogView.findViewById<ImageView>(R.id.ivPlaceholder)
        
        // Store references for image picker callback
        currentDialogAvatarView = avatarImage
        currentDialogPlaceholder = placeholderImage
        pendingAvatarUri = existingContact?.avatarUri ?: ""

        existingContact?.let {
            nameInput.setText(it.name)
            phoneInput.setText(it.phone)
            relationshipInput.setText(it.relationship)
            
            if (it.avatarUri.isNotEmpty()) {
                avatarImage.visibility = View.VISIBLE
                placeholderImage.visibility = View.GONE
                Glide.with(this)
                    .load(File(it.avatarUri))
                    .circleCrop()
                    .into(avatarImage)
            }
        }
        
        // Avatar picker click
        avatarCard.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingContact == null) "Add Contact" else "Edit Contact")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val relationship = relationshipInput.text.toString().trim()

                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    val contact = EmergencyContact(
                        id = existingContact?.id ?: "",
                        name = name,
                        phone = phone,
                        relationship = relationship,
                        avatarUri = pendingAvatarUri,
                        isPrimary = existingContact?.isPrimary ?: (contactsRepository.getContacts().isEmpty())
                    )
                    contactsRepository.saveContact(contact)
                    loadContacts()
                    Toast.makeText(context, "Contact saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Name and phone are required", Toast.LENGTH_SHORT).show()
                }
                
                // Clear references
                currentDialogAvatarView = null
                currentDialogPlaceholder = null
                pendingAvatarUri = ""
            }
            .setNegativeButton("Cancel") { _, _ ->
                currentDialogAvatarView = null
                currentDialogPlaceholder = null
                pendingAvatarUri = ""
            }
            .show()
    }

    private fun showContactMenu(contact: EmergencyContact, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add("Edit")
            menu.add("Set as Primary")
            menu.add("Delete")

            setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Edit" -> showAddContactDialog(contact)
                    "Set as Primary" -> {
                        contactsRepository.setPrimaryContact(contact.id)
                        loadContacts()
                        Toast.makeText(context, "${contact.name} is now primary", Toast.LENGTH_SHORT).show()
                    }
                    "Delete" -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete Contact")
                            .setMessage("Remove ${contact.name} from emergency contacts?")
                            .setPositiveButton("Delete") { _, _ ->
                                contactsRepository.deleteContact(contact.id)
                                loadContacts()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                true
            }
            show()
        }
    }

    private fun callContact(contact: EmergencyContact) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${contact.phone}")
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
