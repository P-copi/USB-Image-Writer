from pathlib import Path
from PIL import Image, ImageDraw
import piexif
folder=Path("app/src/main/assets")
folder.mkdir(parents=True,exist_ok=True)
for with_gps in [True,False]:
    im=Image.new("RGB",(1200,675),(226,239,251))
    d=ImageDraw.Draw(im)
    d.rounded_rectangle((60,60,1140,615),radius=34,fill=(243,249,253),outline=(84,135,169),width=6)
    d.ellipse((465,120,735,390),fill=(24,113,193),outline=(12,76,133),width=8)
    d.ellipse((560,215,640,295),fill=(241,249,253))
    d.polygon(((600,505),(492,332),(708,332)),fill=(24,113,193))
    d.text((390,555),"SAMPLE GPS EXIF" if with_gps else "NO GPS EXIF",fill=(14,65,112))
    out=folder/("sample_gps.jpg" if with_gps else "sample_no_gps.jpg")
    if with_gps:
      gps={piexif.GPSIFD.GPSLatitudeRef:b"N",
           piexif.GPSIFD.GPSLatitude:((13,1),(45,1),(227916,10000)),
           piexif.GPSIFD.GPSLongitudeRef:b"E",
           piexif.GPSIFD.GPSLongitude:((100,1),(30,1),(6354,1000))}
      im.save(out,format="JPEG",quality=88,exif=piexif.dump({"GPS":gps}))
    else: im.save(out,format="JPEG",quality=88)
    assert out.exists() and out.stat().st_size>1000
print("Example image assets generated")