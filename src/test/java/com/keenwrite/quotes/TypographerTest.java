/* Copyright 2021 White Magic Software, Ltd. -- All rights reserved. */
package com.keenwrite.quotes;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to make sure that the {@link Typographer} can replace straight quotes
 * without affecting preformatted HTML text elements.
 */
class TypographerTest {
  private static final String LINE_SEP = lineSeparator();

  @Test
  void test_Quotes_Straight_Curly() throws IOException {
    Typographer.curl( read( "17165.html" ) );
  }

  @SuppressWarnings( "SameParameterValue" )
  private String read( final String filename ) throws IOException {
    try( final var reader = openReader( filename ) ) {
      return reader.lines().collect( joining( LINE_SEP ) );
    }
  }

  /**
   * Opens a text file for reading. Callers are responsible for closing.
   *
   * @param filename The file to open.
   * @return An instance of {@link BufferedReader} that can be used to
   * read all the lines in the file.
   */
  private BufferedReader openReader( final String filename ) {
    return new BufferedReader( new InputStreamReader( openStream( filename ) ) );
  }

  private InputStream openStream( final String filename ) {
    final var is = getClass().getResourceAsStream( filename );
    assertNotNull( is );

    return is;
  }
}
