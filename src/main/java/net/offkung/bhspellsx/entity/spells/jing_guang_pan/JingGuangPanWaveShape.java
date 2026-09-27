package net.offkung.bhspellsx.entity.spells.jing_guang_pan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.*;

/** Shared render mesh and continuous convex-prism SAT. All vertices are relative to the eye-ray anchor. */
public final class JingGuangPanWaveShape {
    private static final Vec3[] BOX_AXES = {new Vec3(1,0,0), new Vec3(0,1,0), new Vec3(0,0,1)};
    public final Vec3 forward, across, up;
    private final List<Prism> prisms = new ArrayList<>();
    private final AABB bounds;
    private record Prism(Vec3[] vertices, List<Vec3> axes) {}

    public JingGuangPanWaveShape(Vec3 direction, float yaw, boolean left) {
        forward = direction.normalize();
        double y = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(y), 0, Math.sin(y));
        Vec3 vertical = forward.cross(right).normalize(); // Stable at vertical pitch; yaw remains known.
        double roll = Math.toRadians(left ? WAVE_LEFT_ROLL : WAVE_RIGHT_ROLL);
        across = right.scale(Math.cos(roll)).add(vertical.scale(Math.sin(roll)));
        up = vertical.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll)));
        AABB box = null;
        for (int i=0; i<WAVE_SEGMENTS; i++) {
            Vec3[] vertices = new Vec3[8];
            for (int face=0; face<2; face++) {
                double depth = (face == 0 ? -1 : 1) * WAVE_DEPTH / 2;
                vertices[face*4] = point(i, -1, 1, depth);
                vertices[face*4+1] = point(i+1, -1, 1, depth);
                vertices[face*4+2] = point(i+1, 1, 1, depth);
                vertices[face*4+3] = point(i, 1, 1, depth);
            }
            List<Vec3> axes = new ArrayList<>(List.of(BOX_AXES));
            axes.add(up);
            for (int j=0; j<4; j++) {
                Vec3 edge = vertices[(j+1)%4].subtract(vertices[j]);
                addAxis(axes, edge.cross(up));
                for (Vec3 axis : BOX_AXES) addAxis(axes, edge.cross(axis));
            }
            for (Vec3 axis : BOX_AXES) addAxis(axes, up.cross(axis));
            prisms.add(new Prism(vertices, axes));
            for (Vec3 v : vertices) {
                AABB p = new AABB(v, v);
                box = box == null ? p : box.minmax(p);
            }
        }
        bounds = box;
    }
    private static void addAxis(List<Vec3> axes, Vec3 axis) {
        if (axis.lengthSqr() <= 1.0E-16) return;
        Vec3 unit=axis.normalize();
        for (Vec3 existing:axes) if (Math.abs(existing.dot(unit))>1-1.0E-10) return;
        axes.add(unit);
    }
    public Vec3 point(int edge, double side, double widthScale, double depth) {
        return pointAt((double)edge / WAVE_SEGMENTS, side, widthScale, depth);
    }
    /** Same continuous blade surface for the coarse collider and finer render mesh. */
    public Vec3 pointAt(double t, double side, double widthScale, double depth) {
        double angle = Math.toRadians((t-.5)*WAVE_ARC_DEGREES);
        double taper = Math.pow(Math.max(0, Math.sin(Math.PI*t)), WAVE_TAPER_POWER);
        double radius = WAVE_RADIUS + side * WAVE_THICKNESS * .5 * widthScale * taper;
        // Convex edge faces forward; up is the normal of the rolled slash plane.
        return across.scale(Math.sin(angle)*radius).add(forward.scale(Math.cos(angle)*radius-WAVE_RADIUS))
                .add(up.scale(depth));
    }
    public AABB sweptBounds(Vec3 origin, Vec3 delta) { return bounds.move(origin).expandTowards(delta).inflate(1.0E-7); }
    /** First t in [0,1], or infinity. Sweep all 12 convex volumes, not their enclosing box. */
    public double hit(AABB target, Vec3 origin, Vec3 delta) {
        double result = Double.POSITIVE_INFINITY;
        Vec3 center = target.getCenter().subtract(origin);
        Vec3 half = new Vec3(target.getXsize()/2, target.getYsize()/2, target.getZsize()/2);
        for (Prism prism : prisms) {
            double enter=0, leave=1;
            for (Vec3 axis : prism.axes) {
                double min=Double.POSITIVE_INFINITY, max=Double.NEGATIVE_INFINITY;
                for (Vec3 v : prism.vertices) { double p=v.dot(axis); min=Math.min(min,p); max=Math.max(max,p); }
                double c=center.dot(axis), h=Math.abs(axis.x)*half.x+Math.abs(axis.y)*half.y+Math.abs(axis.z)*half.z;
                double speed=delta.dot(axis);
                if (Math.abs(speed)<1.0E-12) {
                    if (max<c-h-1.0E-9 || min>c+h+1.0E-9) { enter=2; break; }
                } else {
                    double a=(c-h-max)/speed, z=(c+h-min)/speed;
                    enter=Math.max(enter,Math.min(a,z)); leave=Math.min(leave,Math.max(a,z));
                    if (enter>leave+1.0E-9) break;
                }
            }
            if (enter<=leave+1.0E-9 && enter<=1 && leave>=0) result=Math.min(result,Math.max(0,enter));
        }
        return result;
    }
}
