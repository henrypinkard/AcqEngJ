/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acqj.util.xytiling;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.internal.Engine;

/**
 * Convenience class for using an Affine transform to translate between pixel coordinates and stage coordinates
 * when doing XY tiled imaging
 *
 * @author henrypinkard
 */
public class CameraTilingStageTranslator {


   private static final String COORDINATES_KEY = "DeviceCoordinatesUm";
   
   private AffineTransform affine_;
   private String xyStageName_;
   private int tileWidth_, tileHeight_, displayTileHeight_, displayTileWidth_, overlapX_, overlapY_;
   private List<XYStagePosition> positionList_ = new ArrayList<XYStagePosition>();
   private final Point2D.Double tile00Center_;

   public CameraTilingStageTranslator(AffineTransform transform, String xyStageName, int width,
                                      int height, int overlapX, int overlapY, Point2D.Double tile00Center) {
      affine_ = transform;
      xyStageName_ = xyStageName;
      tileWidth_ = width;
      tileHeight_ = height;
      overlapX_ = overlapX;
      overlapY_ = overlapY;
      displayTileWidth_ = tileWidth_ - overlapX_;
      displayTileHeight_ = tileHeight_ - overlapY_;
      overlapX_ = overlapX;
      overlapY_ = overlapY;
      tile00Center_ = tile00Center;
   }

   public Point getTileIndicesFromDisplayedPixel(double magnification, int x, int y,
                                                 double viewOffsetX, double viewOffsetY) {
//      double scale = display_.getMagnification();
//      int fullResX = (int) ((x / magnification) + display_.getViewOffset().x);
//      int fullResY = (int) ((y / magnification) + display_.getViewOffset().y);
      int fullResX = (int) ((x / magnification) + viewOffsetX);
      int fullResY = (int) ((y / magnification) + viewOffsetY);
      int xTileIndex = fullResX / getDisplayTileWidth() - (fullResX >= 0 ? 0 : 1);
      int yTileIndex = fullResY / getDisplayTileHeight() - (fullResY >= 0 ? 0 : 1);
      return new Point(xTileIndex, yTileIndex);
   }


   /**
    * return the pixel location in coordinates at appropriate res level of the
    * top left pixel for the given row/column
    *
    * @param row
    * @param col
    * @return
    */
   public Point getDisplayedPixel(double magnification, long row, long col,
                                  double viewOffsetX, double viewOffsetY) {
//      double scale = display_.getMagnification();

      int x = (int) ((col * getDisplayTileWidth() - viewOffsetX) * magnification);
      int y = (int) ((row * getDisplayTileHeight() - viewOffsetY) * magnification);
      return new Point(x, y);
   }


//
//   public Point2D.Double stageCoordsFromPixelCoords(int x, int y) {
//      return stageCoordsFromPixelCoords(x, y, display_.getMagnification(),
//              display_.getViewOffset());
//   }

   /**
    *
    * @param absoluteX x coordinate in the full Res stitched image
    * @param absoluteY y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public Point2D.Double stageCoordsFromPixelCoords(int absoluteX, int absoluteY,
                                                    double mag, Point2D.Double offset) {
      long newX = (long) (absoluteX / mag + offset.x);
      long newY = (long) (absoluteY / mag + offset.y);
      return getStageCoordsFromPixelCoords(newX, newY);
   }


   /*
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public Point pixelCoordsFromStageCoords(double x, double y, double magnification,
                                           Point2D.Double offset) {
      Point fullResCoords = getPixelCoordsFromStageCoords(x, y);
      return new Point(
              (int) ((fullResCoords.x - offset.x) * magnification),
              (int) ((fullResCoords.y - offset.y) * magnification));
   }

   /**
    *
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public synchronized Point2D.Double getStageCoordsFromPixelCoords(long xAbsolute, long yAbsolute) {
      double existingX = tile00Center_.x;
      double existingY = tile00Center_.y;
      double existingRow = 0;
      double existingColumn = 0;
      //get pixel displacement from center of the tile we have coordinates for
      long dxPix = (long) (xAbsolute - (existingColumn + 0.5) * displayTileWidth_);
      long dyPix = (long) (yAbsolute - (existingRow + 0.5) * displayTileHeight_);

      Point2D.Double stagePos = new Point2D.Double();
      double[] mat = new double[4];
      affine_.getMatrix(mat);
      AffineTransform transform = new AffineTransform(mat[0], mat[1], mat[2], mat[3], existingX, existingY);
      transform.transform(new Point2D.Double(dxPix, dyPix), stagePos);
      return stagePos;
   }

   /**
    *
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public synchronized Point getPixelCoordsFromStageCoords(double stageX, double stageY) {
      try {
         double existingX = tile00Center_.x;
         double existingY = tile00Center_.y;
         double existingRow = 0;
         double existingColumn = 0;
         //get stage displacement from center of the tile we have coordinates for
         double dx = stageX - existingX;
         double dy = stageY - existingY;
         AffineTransform transform = (AffineTransform) affine_.clone();
         Point2D.Double pixelOffset = new Point2D.Double(); // offset in number of pixels from the center of this tile
         transform.inverseTransform(new Point2D.Double(dx, dy), pixelOffset);
         //Add pixel offset to pixel center of this tile to get absolute pixel position
         int xPixel = (int) ((existingColumn + 0.5) * displayTileWidth_ + pixelOffset.x);
         int yPixel = (int) ((existingRow + 0.5) * displayTileHeight_ + pixelOffset.y);
         return new Point(xPixel, yPixel);
      } catch (NoninvertibleTransformException e) {
         throw new RuntimeException("Problem using affine transform to convert stage coordinates to pixel coordinates");
      }
   }

   public synchronized XYStagePosition getXYPosition(int index) {
      return new XYStagePosition(positionList_.get(index).getCenter(),
              positionList_.get(index).getGridRow(), positionList_.get(index).getGridCol() );
//
//         JSONArray jsonPos = new JSONArray(positionList_.get(index).toJSON(xyStageName_).getJSONObject(
//                 "DeviceCoordinatesUm").getJSONArray(xyStageName_).toString());
//         Point2D.Double posCenter = new Point2D.Double(jsonPos.getDouble(0), jsonPos.getDouble(1));
//         int gridRow = (int) AcqEngMetadata.getGridRow(positionList_.get(index).toJSON(xyStageName_));
//         int gridCol = (int) AcqEngMetadata.getGridCol(positionList_.get(index).toJSON(xyStageName_));
//         return new XYStagePosition(posCenter, gridRow, gridCol);

   }

   public int getFullResPositionIndexFromStageCoords(double x, double y) {
      Point pixelCoords = getPixelCoordsFromStageCoords(x, y);
      long rowIndex = Math.round((double) (pixelCoords.y - displayTileHeight_ / 2) / (double) displayTileHeight_);
      long colIndex = Math.round((double) (pixelCoords.x - displayTileWidth_ / 2) / (double) displayTileWidth_);
      int[] posIndex = getPositionIndices(new int[]{(int) rowIndex}, new int[]{(int) colIndex});
      return posIndex[0];
   }

   public List<XYStagePosition> getPositionList() {
      ArrayList<XYStagePosition> list = new ArrayList<XYStagePosition>();
      for (int i = 0; i < positionList_.size(); i++) {
         list.add(getXYPosition(i));
      }
      return list;
   }
   
   
   /**
    * Calculate the x and y stage coordinates of a new position given its row
    * and column and the existing metadata for another position
    *
    * @param row
    * @param col
    * @return
    */
   private synchronized Point2D.Double getStagePositionCoordinates(int row, int col, int pixelOverlapX, int pixelOverlapY) {
      double existingX = tile00Center_.x;
      double existingY = tile00Center_.y;
      double existingRow = 0;
      double existingColumn = 0;

      double xPixelOffset = (col - existingColumn) * (Engine.getCore().getImageWidth() - pixelOverlapX);
      double yPixelOffset = (row - existingRow) * (Engine.getCore().getImageHeight() - pixelOverlapY);

      Point2D.Double stagePos = new Point2D.Double();
      double[] mat = new double[4];
      affine_.getMatrix(mat);
      AffineTransform transform = new AffineTransform(mat[0], mat[1], mat[2], mat[3], existingX, existingY);
      transform.transform(new Point2D.Double(xPixelOffset, yPixelOffset), stagePos);
      return stagePos;

   }

   /**
    * Set a grid of XY stage positions, as generated from some external source, so that they can be used
    * to in caculations relating pixel and stage coordinates
    * @param positions
    */
   public void setPositions(List<XYStagePosition> positions) {
      positionList_ = positions;
   }

     /**
    * Return the position indices for the positions at the specified rows, cols.
    * If no position exists at this location, create one and return its index
    *
    * @param rows
    * @param cols
    * @return
    */
     public synchronized int[] getPositionIndices(int[] rows, int[] cols) {
        int[] posIndices = new int[rows.length];
        boolean newPositionsAdded = false;

        outerloop:
        for (int h = 0; h < posIndices.length; h++) {
           //check if position is already present in list, and if so, return its index
           for (int i = 0; i < positionList_.size(); i++) {
              if (positionList_.get(i).getGridRow() == rows[h]
                      && positionList_.get(i).getGridCol() == cols[h]) {
                 //we already have position, so return its index
                 posIndices[h] = i;
                 continue outerloop;
              }
           }
           //add this position to list

           Point2D.Double stageCoords = getStagePositionCoordinates(rows[h], cols[h], overlapX_, overlapY_);
           positionList_.add(new XYStagePosition(stageCoords, rows[h], cols[h]));
           newPositionsAdded = true;

           posIndices[h] = positionList_.size() - 1;
        }
        //if size of grid wasn't expanded, return here
        if (!newPositionsAdded) {
           return posIndices;
        }

        return posIndices;
     }

   public int getDisplayTileHeight() {
      return displayTileHeight_;
   }

   public int getDisplayTileWidth() {
      return displayTileWidth_;
   }
   
   public Point2D.Double[] getDisplayTileCornerStageCoords(XYStagePosition pos) {
      return pos.getVisibleTileCorners(overlapX_, overlapY_);
   }
 
}
