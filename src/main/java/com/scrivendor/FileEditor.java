/*
 * Copyright (c) 2015 Karl Tauber <karl at jformdesigner dot com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.scrivendor;

import com.scrivendor.editor.MarkdownEditorPane;
import com.scrivendor.preview.HTMLPreviewPane;
import com.scrivendor.service.Options;
import com.scrivendor.service.events.AlertMessage;
import com.scrivendor.service.events.AlertService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.input.InputEvent;
import javafx.scene.text.Text;
import org.fxmisc.undo.UndoManager;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;

/**
 * Editor for a single file.
 *
 * @author Karl Tauber
 */
class FileEditor {

  private final Options options = Services.load( Options.class );
  private final AlertService alertService = Services.load( AlertService.class );

  private final Tab tab = new Tab();
  private MarkdownEditorPane markdownEditorPane;
  private HTMLPreviewPane markdownPreviewPane;

  private final ObjectProperty<Path> path = new SimpleObjectProperty<>();
  private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper();
  private final BooleanProperty canUndo = new SimpleBooleanProperty();
  private final BooleanProperty canRedo = new SimpleBooleanProperty();

  FileEditor( final Path path ) {
    this.path.set( path );

    // avoid that this is GCed
    tab.setUserData( this );

    this.path.addListener( (observable, oldPath, newPath) -> updateTab() );
    this.modified.addListener( (observable, oldPath, newPath) -> updateTab() );
    updateTab();

    tab.setOnSelectionChanged( e -> {
      if( tab.isSelected() ) {
        Platform.runLater( () -> activated() );
      }
    } );
  }

  private void updateTab() {
    final Tab activeTab = getTab();
    final Path filePath = getPath();
    activeTab.setText( (filePath != null) ? filePath.getFileName().toString() : Messages.get( "FileEditor.untitled" ) );
    activeTab.setTooltip( (filePath != null) ? new Tooltip( filePath.toString() ) : null );
    activeTab.setGraphic( isModified() ? new Text( "*" ) : null );
  }

  private void activated() {
    final Tab activeTab = getTab();

    if( activeTab.getTabPane() == null || !activeTab.isSelected() ) {
      // Tab is closed or no longer active
      return;
    }

    final MarkdownEditorPane editorPane = getEditorPane();
    editorPane.pathProperty().bind( this.path );

    if( activeTab.getContent() != null ) {
      editorPane.requestFocus();
      return;
    }

    // Load file and create UI when the tab becomes visible the first time.
    final HTMLPreviewPane previewPane = getPreviewPane();

    // The Markdown Preview Pane must receive the load event.
    editorPane.addChangeListener( previewPane );

    // Bind preview to editor.
    previewPane.pathProperty().bind( pathProperty() );
    previewPane.scrollYProperty().bind( editorPane.scrollYProperty() );

    // Load the text and update the preview before the undo manager.
    load();

    // Track undo requests (only after loading).
    initUndoManager();

    // Separate the edit and preview panels.
    SplitPane splitPane = new SplitPane(
      editorPane.getScrollPane(),
      previewPane.getWebView() );
    activeTab.setContent( splitPane );

    // Set the caret position to 0.
    editorPane.scrollToTop();

    // Let the user edit.
    editorPane.requestFocus();
  }

  private void initUndoManager() {
    final UndoManager undoManager = getUndoManager();

    // Clear undo history after first load.
    undoManager.forgetHistory();

    // Bind the editor undo manager to the properties.
    modified.bind( Bindings.not( undoManager.atMarkedPositionProperty() ) );
    canUndo.bind( undoManager.undoAvailableProperty() );
    canRedo.bind( undoManager.redoAvailableProperty() );
  }

  void load() {
    final Path filePath = getPath();

    if( filePath != null ) {
      try {
        final byte[] bytes = Files.readAllBytes( filePath );

        String markdown;

        try {
          markdown = new String( bytes, getOptions().getEncoding() );
        } catch( Exception e ) {
          // Unsupported encodings and null pointers will fallback here.
          markdown = new String( bytes );
        }

        getEditorPane().setMarkdown( markdown );
        getEditorPane().getUndoManager().mark();
      } catch( IOException ex ) {
        final AlertMessage message = getAlertService().createAlertMessage(
          Messages.get( "FileEditor.loadFailed.title" ),
          Messages.get( "FileEditor.loadFailed.message" ),
          filePath,
          ex.getMessage()
        );

        final Alert alert = getAlertService().createAlertError( message );

        alert.showAndWait();
      }
    }
  }

  boolean save() {
    final String markdown = getEditorPane().getMarkdown();

    byte[] bytes;

    try {
      bytes = markdown.getBytes( getOptions().getEncoding() );
    } catch( Exception ex ) {
      bytes = markdown.getBytes();
    }

    try {
      Files.write( getPath(), bytes );
      getEditorPane().getUndoManager().mark();
      return true;
    } catch( IOException ex ) {
      final AlertService service = getAlertService();
      final AlertMessage message = service.createAlertMessage(
        Messages.get( "FileEditor.saveFailed.title" ),
        Messages.get( "FileEditor.saveFailed.message" ),
        getPath(),
        ex.getMessage()
      );

      final Alert alert = service.createAlertError( message );

      alert.showAndWait();
      return false;
    }
  }

  Tab getTab() {
    return this.tab;
  }

  Path getPath() {
    return this.path.get();
  }

  void setPath( Path path ) {
    this.path.set( path );
  }

  ObjectProperty<Path> pathProperty() {
    return this.path;
  }

  boolean isModified() {
    return this.modified.get();
  }

  ReadOnlyBooleanProperty modifiedProperty() {
    return this.modified.getReadOnlyProperty();
  }

  BooleanProperty canUndoProperty() {
    return this.canUndo;
  }

  BooleanProperty canRedoProperty() {
    return this.canRedo;
  }

  private UndoManager getUndoManager() {
    return getEditorPane().getUndoManager();
  }

  public <T extends Event, U extends T> void addEventListener(
    final EventPattern<? super T, ? extends U> event,
    final Consumer<? super U> consumer ) {
    getEditorPane().addEventListener( event, consumer );
  }

  public void addEventListener( final InputMap<InputEvent> map ) {
    getEditorPane().addEventListener( map );
  }

  public void removeEventListener( final InputMap<InputEvent> map ) {
    getEditorPane().removeEventListener( map );
  }

  protected HTMLPreviewPane getPreviewPane() {
    if( this.markdownPreviewPane == null ) {
      this.markdownPreviewPane = new HTMLPreviewPane();
    }

    return this.markdownPreviewPane;
  }

  protected MarkdownEditorPane getEditorPane() {
    if( this.markdownEditorPane == null ) {
      this.markdownEditorPane = new MarkdownEditorPane();
    }

    return this.markdownEditorPane;
  }

  private AlertService getAlertService() {
    return this.alertService;
  }

  private Options getOptions() {
    return this.options;
  }
}
