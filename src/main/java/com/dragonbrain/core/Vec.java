package com.dragonbrain.core;

public record Vec(double x, double y, double z) {
   public static final Vec ZERO = new Vec(0.0, 0.0, 0.0);

   public Vec add(Vec other) {
      return new Vec(this.x + other.x, this.y + other.y, this.z + other.z);
   }

   public Vec subtract(Vec other) {
      return new Vec(this.x - other.x, this.y - other.y, this.z - other.z);
   }

   public Vec multiply(double factor) {
      return new Vec(this.x * factor, this.y * factor, this.z * factor);
   }

   public double length() {
      return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
   }

   public double lengthSquared() {
      return this.x * this.x + this.y * this.y + this.z * this.z;
   }

   public double horizontalLength() {
      return Math.sqrt(this.x * this.x + this.z * this.z);
   }

   public double distanceTo(Vec other) {
      return this.subtract(other).length();
   }

   public Vec normalize() {
      double len = this.length();
      return len < 1.0E-6 ? ZERO : this.multiply(1.0 / len);
   }

   public Vec withY(double newY) {
      return new Vec(this.x, newY, this.z);
   }

   public double dot(Vec other) {
      return this.x * other.x + this.y * other.y + this.z * other.z;
   }
}
