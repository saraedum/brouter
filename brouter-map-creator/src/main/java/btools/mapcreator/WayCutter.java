package btools.mapcreator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;

import btools.util.*;

/**
 * WayCutter does 2 step in map-processing:
 *
 * - cut the way file into 45*30 - pieces
 * - enrich ways with relation information
 *
 * @author ab
 */
public class WayCutter extends MapCreatorBase
{
  private CompactLongSet cyclewayset;
  private DenseLongMap tileIndexMap;

  public static void main(String[] args) throws Exception
  {
    System.out.println("*** WayCutter: Soft-Cut way-data into tiles");
    if (args.length != 4)
    {
      System.out.println("usage: java WayCutter <node-tiles-in> <way-file-in> <way-tiles-out> <relation-file>" );

      return;
    }
    new WayCutter().process( new File( args[0] ), new File( args[1] ), new File( args[2] ), new File( args[3] ) );
  }

  public void process( File nodeTilesIn, File wayFileIn, File wayTilesOut, File relationFileIn ) throws Exception
  {
    this.outTileDir = wayTilesOut;

    // *** read the relation file into a set (currently cycleway processing only)
    cyclewayset = new CompactLongSet();
    DataInputStream dis = createInStream( relationFileIn );
    try
    {
      for(;;)
      {
        long wid = readId( dis );
        if ( !cyclewayset.contains( wid ) ) cyclewayset.add( wid );
      }
    }
    catch( EOFException eof )
    {
      dis.close();
    }
    System.out.println( "marked cycleways: " + cyclewayset.size() );


    // *** read all nodes into tileIndexMap
    tileIndexMap = Boolean.getBoolean( "useDenseMaps" ) ? new DenseLongMap( 6 ) : new TinyDenseLongMap();
    new NodeIterator( this, false ).processDir( nodeTilesIn, ".tlf" );

    // *** finally process the way-file, cutting into pieces
    new WayIterator( this, true ).processFile( wayFileIn );
    closeTileOutStreams();
  }

  @Override
  public void nextNode( NodeData n ) throws Exception
  {
    tileIndexMap.put( n.nid, getTileIndex( n.ilon, n.ilat ) );
  }

  @Override
  public void nextWay( WayData data ) throws Exception
  {
    // propagate the cycleway-bit
    if ( cyclewayset.contains( data.wid ) )
    {
      data.description |= 2;
    }

    long waytileset = 0;
    int nnodes = data.nodes.size();

    // determine the tile-index for each node
    for (int i=0; i<nnodes; i++ )
    {
      int tileIndex = tileIndexMap.getInt( data.nodes.get(i) );
      if ( tileIndex != -1 )
      {
        waytileset |= ( 1L << tileIndex );
      }
    }

    // now write way to all tiles hit
    for( int tileIndex=0; tileIndex<54; tileIndex++ )
    {
      if ( ( waytileset & ( 1L << tileIndex ) ) == 0 )
      {
        continue;
      }
      data.writeTo( getOutStreamForTile( tileIndex ) );
    }
  }

  private int getTileIndex( int ilon, int ilat )
  {
     int lon = ilon / 45000000;
     int lat = ilat / 30000000;
     if ( lon < 0 || lon > 7 || lat < 0 || lat > 5 ) throw new IllegalArgumentException( "illegal pos: " + ilon + "," + ilat );
     return lon*6 + lat;
  }

  protected String getNameForTile( int tileIndex )
  {
    int lon = (tileIndex / 6 ) * 45 - 180;
    int lat = (tileIndex % 6 ) * 30 - 90;
    String slon = lon < 0 ? "W" + (-lon) : "E" + lon;
    String slat = lat < 0 ? "S" + (-lat) : "N" + lat;
    return slon + "_" + slat + ".wtl";
  }

}